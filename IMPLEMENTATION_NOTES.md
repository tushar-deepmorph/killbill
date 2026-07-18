# Kill Bill #2040 implementation notes

## Approach

- Added the account-level `PAID_BY_EXTERNAL` system tag with the stable UUID
  `00000000-0000-0000-0000-00000000000a`. The invoice-created payment bus
  handler checks this tag before starting automatic payment, so tagged accounts
  retain an unpaid invoice balance and continue through normal overdue handling.
  Existing `MANUAL_PAY`, `AUTO_PAY_OFF`, and untagged account behavior is unchanged.
- Added `ExternalInvoicePaymentApi` and an internal multi-invoice purchase method.
  A reconciliation call creates one external payment and one purchase transaction,
  carrying an ordered map of invoice allocations through the invoice payment
  control plugin.
- Extended the invoice payment control plugin to initialize and complete a normal
  `invoice_payments` attempt for every allocated invoice. Allocations can be full,
  partial, or greater than the invoice balance.
- Successful payment completion now runs existing CBA logic. Consequently, an
  allocation above the outstanding balance creates account credit through the
  standard `CBA_ADJ` machinery.
- Added `POST /1.0/kb/invoices/payments/external`. The request contains `accountId`,
  optional external/effective-date fields, and an `allocations` array of
  `{ "invoiceId": ..., "amount": ... }` objects. The endpoint returns the created
  payment, whose single purchase transaction is linked to all allocated invoices.

## Files changed

- `api`: external-payment service contract, multi-invoice internal contract, and
  the stable `PAID_BY_EXTERNAL` tag identifier.
- `util`: system-tag registration/lookup, SQL tag-definition union, and a focused
  system-tag test.
- `payment`: automatic-payment suppression, external reconciliation service,
  Guice binding, and multi-invoice invoice-payment-control bookkeeping.
- `invoice`: CBA refresh after successful payment completion and an overpayment
  test.
- `jaxrs`: reconciliation request JSON, endpoint, and constructor test update.

## Verification

- `mvn -q -DskipTests compile` passes with the available JDK 25.
- `mvn -q -DskipTests test-compile` passes for the full reactor.
- The focused `TestSystemTags` run cannot start in this environment because the
  repository's Mockito/Byte Buddy version supports Java class files only through
  Java 21, while the build enforcer requires Java 21+ and the available compatible
  build JDK is Java 25. The failure occurs in the suite setup before the test runs.

## Known limitations

- `ControlTagType` is supplied by the separately versioned `killbill-api` artifact.
  Since that external artifact does not yet contain `PAID_BY_EXTERNAL`, this change
  registers the tag in the same system-tag machinery and exposes its stable ID via
  `PaidByExternalTag` instead of adding an enum member to the external dependency.
  A future `killbill-api` release can add the matching enum constant without a data
  migration.
- Cross-currency allocations are intentionally rejected; every allocated invoice
  must use the account/payment currency.
