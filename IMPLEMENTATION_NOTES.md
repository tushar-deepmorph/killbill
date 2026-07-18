# Implementation Notes — killbill/killbill#2040

**Better Support For Customer-Initiated External Payment Scenarios**

## Summary

When a customer intends to pay an invoice out of band (bank transfer, internet banking,
etc.) days or weeks after the invoice is generated, Kill Bill should *not* auto-generate a
synthetic payment that marks the invoice paid. The invoice must stay unpaid so that the
normal overdue / subscription-blocking rules apply, and the biller must be able to record
the real incoming bank transaction later — against one or more invoices — during
reconciliation.

This change adds:

1. A new **account-level system tag `PAID_BY_EXTERNAL`**. When present on an account,
   automatic (invoice-triggered) payments are suppressed; invoices stay unpaid.
2. A new **"record external payment" REST endpoint** that lets a biller record a
   customer-initiated bank transaction across one or more invoices, supporting full,
   partial, over-payment (excess → account credit / CBA) and multi-invoice allocation.

Both flow through the existing invoice-payment bookkeeping, so account balance, overdue
calculations and reporting treat them like any other payment.

## Design constraints discovered

- `org.killbill.billing.util.tag.ControlTagType` (which defines `MANUAL_PAY`,
  `AUTO_PAY_OFF`, …) lives in the **external, frozen `killbill-api` jar** — it cannot be
  edited from this repository. So `PAID_BY_EXTERNAL` could not be added as a genuine
  `ControlTagType` enum value.
- The `util` module's `SystemTags` class *is* the in-repo "tag machinery" the issue refers
  to. It already hosts well-known, code-recognized tag definitions that don't need a
  per-tenant `tag_definitions` row (e.g. the internal `__PARK__` tag). This is the seam the
  new tag uses.
- The external `InvoicePaymentApi` / `PaymentApi` interfaces are likewise frozen, so the
  record-external-payment capability is built by *orchestrating* the existing
  `InvoicePaymentApi.createPurchaseForInvoicePayment(..., PaymentOptions{external=true})`
  per invoice, plus `InvoiceUserApi.insertCredits(...)` for over-payment — no new API
  interface methods were required.

## Changes by file

### 1. New system tag (`util`)

- **`util/.../tag/dao/SystemTags.java`**
  - Added `PAID_BY_EXTERNAL_TAG_DEFINITION_ID` (`new UUID(1, 2)`, i.e.
    `00000000-0000-0001-0000-000000000002`) and `PAID_BY_EXTERNAL_TAG_DEFINITION_NAME`.
  - Introduced a second category of system tags: `USER_ASSIGNABLE_SYSTEM_TAG_DEFINITIONS`.
    Unlike the internal `SYSTEM_DEFINED_TAG_DEFINITIONS` (`__PARK__`), these are resolvable
    without a DB row **and** freely addable/removable by users on the applicable object —
    exactly like control tags. `get()`, `lookup(id)` and `lookup(name)` now also resolve
    this category, while `isSystemTag()` deliberately still returns `false` for it so that
    `DefaultTagUserApi.addTag` does not reject it with `TAG_IS_SYSTEM`.
- **`util/.../tag/dao/TagSqlDao.sql.stg`**
  - Added the matching row in `userAndSystemTagDefinitions()` so tag search / joins resolve
    the definition name & description (kept in sync with `SystemTags`, as the file comment
    requires).

The tag applies to `ObjectType.ACCOUNT`. Because it resolves through `SystemTags.lookup`,
the existing tag-creation path (`DefaultTagDao.getTagDefinitionFromTransaction`) accepts it
with no schema/seed migration, and it can be added/removed through the normal
`POST/DELETE /accounts/{accountId}/tags` endpoints.

### 2. Suppress automatic payment (`payment`)

- **`payment/.../invoice/InvoicePaymentControlPluginApi.java`**
  - `getPluginPurchaseResult(...)` now aborts the payment when the payment is **automatic**
    (`!isApiPayment()`) and the account carries `PAID_BY_EXTERNAL`. This is the single
    choke point every invoice-triggered payment passes through, so the invoice simply stays
    unpaid and overdue/blocking logic applies unchanged.
  - Unlike `AUTO_PAY_OFF`, nothing is queued for later auto-retry — the payment is expected
    to arrive out of band and be recorded explicitly.
  - The guard only affects automatic payments; biller-initiated (API) payments — including
    the new record-external-payment endpoint — are **not** blocked.
  - Added helper `isAccountPaidByExternal(...)`.

### 3. Record-external-payment endpoint (`jaxrs`)

- **`jaxrs/.../resources/InvoiceResource.java`** — new endpoint
  `POST /1.0/kb/invoices/payments` → `recordExternalPayments(...)`.
  - Body: a `List<InvoicePaymentJson>`, each entry carrying `accountId`, `targetInvoiceId`
    and `purchasedAmount` (optional `paymentExternalKey`). All entries must belong to the
    same account; a `paymentMethodId` is rejected (this is always an external payment).
  - For each allocation, it records an **external** purchase for
    `min(requestedAmount, invoiceBalance)` via the existing
    `createPurchaseForInvoice(... external PaymentOptions ...)` helper:
    - full payment → invoice balance goes to zero;
    - partial payment → invoice keeps its remaining balance (and stays subject to overdue);
    - multi-invoice → one allocation entry per invoice in a single request.
  - Any amount recorded **beyond** the invoice balances is accumulated and turned into
    account credit (CBA) via `InvoiceUserApi.insertCredits(...)`, so over-payment is not
    lost and is consumed against future invoices.
  - Returns the created `InvoicePayment`s as `InvoicePaymentJson`.

## Tests

- **`util/.../tag/dao/TestSystemTags.java`** (new, fast): `PAID_BY_EXTERNAL` resolves by id
  and name, is *not* flagged as a non-assignable system tag (so users may add it), and is
  always listed as a tag definition.
- **`payment/.../invoice/TestInvoicePaymentControlPluginApiUnit.java`** (extended, fast):
  - automatic payment on a `PAID_BY_EXTERNAL` account is aborted (invoice left unpaid);
  - an API (biller-recorded) payment on the same account is **not** aborted.

All new unit tests pass. `mvn -q -DskipTests compile` passes for the whole project.

### Build / environment note

The project's Maven enforcer requires **JDK 21+**. Compilation was verified on the
default JDK; the unit tests were run on JDK 21 (`openjdk@21`). Mockito's inline mock maker
is incompatible with the JDK 25 that happened to be the machine default, which blocks the
shared test base classes from initializing — unrelated to this change. Running the suite
under JDK 21 works as shown above.

## Backward compatibility

- Accounts **without** the new tag behave exactly as before, including existing
  `MANUAL_PAY` semantics (which only influence invoice-template rendering and are
  untouched).
- No database schema change and no seed migration: the tag is code-defined in `SystemTags`
  and resolved without a `tag_definitions` row. No external API interface was modified.

## Known limitations / possible follow-ups

- Because the external `InvoicePaymentApi` is frozen, a single bank transaction spread
  across N invoices is recorded as N `Payment` objects (one per invoice) rather than one
  `Payment` linked to N invoices. Callers wanting them grouped can pass a shared
  `paymentExternalKey` per entry; distinct payment external keys are otherwise
  auto-generated.
- Over-payment credit is created as an account-level credit (CBA) dated on the request
  date; it is not tied back to a specific originating invoice item.
- Removing the `PAID_BY_EXTERNAL` tag does not retroactively trigger automatic payment of
  invoices that were left unpaid while it was set (this is intentional — those are meant to
  be reconciled via the record-external-payment endpoint). This differs from the
  `AUTO_PAY_OFF` removal behavior.
