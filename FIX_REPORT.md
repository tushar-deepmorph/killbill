# Issue #277 — Ability to override paymentMethod per subscription/bundle

## Root cause / design summary

This is a feature request, not a bug. Today `InvoicePaymentControlPluginApi.priorCall(...)`
(in `payment/src/main/java/org/killbill/billing/payment/invoice/InvoicePaymentControlPluginApi.java`)
processes an invoice payment using only the **account-level** `paymentMethodId` carried in the
`PaymentControlContext`. The class never inspects the invoice items to see whether they all
belong to a single subscription/bundle that should be billed against a different payment
method. The hook to override the payment method exists — `ControlPluginRunner` (line 128)
already honors `PriorPaymentControlResult.getAdjustedPaymentMethodId()` — it just was never
populated by the built-in invoice control plugin.

The fix wires that gap by allowing a per-subscription / per-bundle override to be registered
via the existing **custom fields** mechanism (no new schema, no new endpoint), and by making
`InvoicePaymentControlPluginApi.getPluginPurchaseResult` look the override up and return it
through `DefaultPriorPaymentControlResult.adjustedPaymentMethodId`.

## Change summary

- `payment/src/main/java/org/killbill/billing/payment/invoice/InvoicePaymentControlPluginApi.java`
  - Added the public constant `OVERRIDE_PAYMENT_METHOD_FIELD_NAME = "OVERRIDE_PAYMENT_METHOD_ID"`,
    the well-known custom-field key used to register a per-subscription / per-bundle override.
  - Injected `CustomFieldInternalApi` (already bound in `util/.../CustomFieldModule`, line 47).
  - Added `getPaymentMethodOverride(Invoice, InternalTenantContext)` (`@VisibleForTesting`) that
    - returns `null` when invoice items reference multiple subscriptions **and** multiple
      bundles (the constraint called out in the issue),
    - looks up the override on the subscription first (more specific) and falls back to the
      bundle,
    - tolerates a missing or malformed custom-field value by returning `null`.
  - In `getPluginPurchaseResult`, immediately before returning the success result, calls the
    new helper and — if an override is found that differs from the context's
    `paymentMethodId` — returns a `DefaultPriorPaymentControlResult` carrying
    `adjustedPaymentMethodId`. `ControlPluginRunner` already routes the rest of the payment
    flow through that override (subject to `paymentConfig.isAllowedToOverwritePaymentMethodId()`).

- `payment/src/test/java/org/killbill/billing/payment/invoice/TestInvoicePaymentControlPluginApiUnit.java`
  - Threaded the new `CustomFieldInternalApi` dependency through the test factory.
  - Added four new fast-group unit tests on `getPaymentMethodOverride`:
    - `testPaymentMethodOverride_singleSubscription` — invoice with two items pointing to one
      subscription that carries the override custom field → override is returned.
    - `testPaymentMethodOverride_bundleFallback` — subscription has no override, bundle does →
      bundle override is returned.
    - `testPaymentMethodOverride_multipleSubscriptionsAndBundles_noOverride` — items span
      multiple subscriptions **and** multiple bundles → no override (and the custom-field API
      is not even consulted; verified with `verifyNoInteractions`).
    - `testPaymentMethodOverride_malformedValueIsIgnored` — non-UUID custom-field value →
      `null`, no exception leaks.

No build files were changed. No new tables, migrations, or endpoints were introduced — the
custom-fields REST endpoints already cover registering/removing the override.

## How the tests exercise the new behaviour

The four tests directly drive `getPaymentMethodOverride(...)` over `Invoice` mocks with
specific `InvoiceItem` shapes (subscription/bundle ids) and stub `CustomFieldInternalApi`
responses. They lock in:

1. The "single subscription" case (preferred path),
2. The "single bundle" fallback,
3. The mixed-items abstain rule from the issue ("only work if the invoice contains only one
   subscription/bundle"),
4. Defensive parsing of the custom-field value.

This is the highest-value, lowest-risk seam to test: it independently verifies the policy that
maps invoice → override, without standing up the entire payment state machine.

## Build status

Compile-only run as requested:

```
mvn -pl payment -am -DskipTests compile -Dcheck.skip-spotbugs=true
...
[INFO] Reactor Summary for killbill 0.24.17-SNAPSHOT:
[INFO]
[INFO] killbill ........................................... SUCCESS [  0.170 s]
[INFO] killbill-api ....................................... SUCCESS [  0.182 s]
[INFO] killbill-util ...................................... SUCCESS [  0.403 s]
[INFO] killbill-tenant .................................... SUCCESS [  0.198 s]
[INFO] killbill-account ................................... SUCCESS [  0.183 s]
[INFO] killbill-catalog ................................... SUCCESS [  0.173 s]
[INFO] killbill-subscription .............................. SUCCESS [  0.178 s]
[INFO] killbill-entitlement ............................... SUCCESS [  0.170 s]
[INFO] killbill-junction .................................. SUCCESS [  0.167 s]
[INFO] killbill-usage ..................................... SUCCESS [  0.158 s]
[INFO] killbill-invoice ................................... SUCCESS [  0.168 s]
[INFO] killbill-payment ................................... SUCCESS [  0.170 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  2.471 s
```

Test sources also compile cleanly:

```
mvn -pl payment clean test-compile -Dcheck.skip-spotbugs=true
...
[INFO] BUILD SUCCESS
[INFO] Total time:  2.880 s
```

Note: `-Dcheck.skip-spotbugs=true` is used because the repo's spotbugs check fails in this
environment unrelated to the change (it errors out before reporting any findings). Removing
the flag does not affect compilation; it is only the spotbugs step that fails.

Attempting to actually run the test class in this environment fails inside
`GuicyKillbillTestSuite.globalBeforeTest` with `Mockito cannot mock this class: class
org.killbill.clock.ClockMock` — an incompatibility between the older Mockito version pinned
in the build and Java 25 on this machine. This affects every test in the class equally
(including the pre-existing `testGetNumberAttemptsInState`) and is the same family of
environment limitation the task brief calls out for embedded MySQL ARM. Compile-clean is the
bar.

## Confidence level

**Medium-high.**

What I am confident about:

- The hook (`adjustedPaymentMethodId`) is real, exercised, and respected by
  `ControlPluginRunner` (verified at `payment/.../ControlPluginRunner.java:128`); injecting an
  override at the `priorCall` stage is the documented design path called out in the issue.
- The selection rule (single subscription → check subscription override, else single bundle →
  check bundle override, else abstain) matches the constraint in the issue body verbatim and
  is locked in by tests.
- Re-using custom fields means zero schema/API surface change and the override is already
  manageable through `/1.0/kb/subscriptions/{id}/customFields` (and the equivalent for bundles)
  with no extra plumbing.
- Compilation is clean on the touched module and the rest of the reactor up to `payment`.

What keeps me from "high":

- The full end-to-end flow (priorCall → state machine → gateway dispatch) was not exercised
  here because of the JVM/Mockito and embedded-MySQL constraints noted above. The unit tests
  cover the new code in isolation but not the integration handoff to `ControlPluginRunner`.
- The override-vs-default precedence interacts with `paymentConfig.isAllowedToOverwritePaymentMethodId()`
  (line 131 of `ControlPluginRunner`): if a payment already has a `paymentMethodId` recorded
  and the config flag is `false`, the override will be rejected as today. That is the existing
  guard for #1097 and is correct here, but operators flipping the flag should be aware.
