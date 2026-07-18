# Implementation Notes: Better Support for Customer-Initiated External Payment Scenarios (Issue #2040)

This document provides a detailed overview of the implementation, design decisions, files modified, and verification steps for Kill Bill Issue #2040.

## 1. Feature Description

In modern billing systems, customer-initiated external payment methods (such as bank transfers or internet banking) require a review period before the actual transaction is cleared. Generating a synthetic, automated payment immediately on invoice generation artificially zeroes out the account balance and prevents normal overdue, dunning, or collection flows.

To address this issue, we introduced two main capabilities:
1. **`PAID_BY_EXTERNAL` System Control Tag**: A first-class account-level tag that halts automated synthetic external payments during standard invoice generation, allowing invoices to remain in an unpaid/committed state.
2. **"Record External Payment" Endpoint**: A robust API allowing billers to record bank transactions against accounts and invoices once they are completed by the customer, including support for single-invoice payments, auto-allocations, partial payments, and overpayments/excess (CBA).

---

## 2. Technical Details & Architecture

### A. Tag Machinery & System Registration
We completely emulated first-class control tags (similar to `MANUAL_PAY`/`AUTO_PAY_OFF`) for `PAID_BY_EXTERNAL` across the util and engine layers:
* **UUID Definition**: Registered with ID `00000000-0000-0002-0000-000000000001` (i.e. `new UUID(2, 1)`).
* **System Tag Registry**: Registered in `SystemTags.java` under `SYSTEM_DEFINED_TAG_DEFINITIONS`.
* **Database Mapping**: Injected a SQL union mapping the new UUID, name, and description inside `TagSqlDao.sql.stg`'s `userAndSystemTagDefinitions` block.
* **Control/Validation Rules**:
  * Updated `TagModelDaoHelper.java` to classify it as a control tag under string/UUID checks.
  * Restrained applicability exclusively to `ObjectType.ACCOUNT` in `DefaultTagDefinition.java` and verified validation constraints in `DefaultTagDao.java`.

### B. Automated Payment Prevention Hook
During automated billing, when an invoice is created, standard event handlers trigger automatic payment collection attempts.
* We intercepted this in `InvoicePaymentControlPluginApi.java` inside `getPluginPurchaseResult` (which runs before the payment attempt executes).
* If the payment is **not** an API payment (meaning it's an automated payment run), the payment is using the external payment plugin (`__EXTERNAL_PAYMENT__`), and the account carries the `PAID_BY_EXTERNAL` tag, the payment is aborted immediately by returning a `DefaultPriorPaymentControlResult(true)`.
* This leaves the invoice in an unpaid/committed state, enabling standard dunning/overdue flows.

### C. JAX-RS API & Record External Payment
To record the actual cash receipts after they clear:
* Created `ExternalPaymentJson.java` representing the request payload:
  * `accountId` (required)
  * `amount` (required)
  * `currency` (required)
  * `paymentMethodId` (optional)
  * `paymentExternalKey` (optional)
  * `transactionExternalKey` (optional)
  * `effectiveDate` (optional)
  * `allocations` (optional map of invoice IDs to allocated amounts)
* Implemented the JAX-RS endpoint inside `InvoicePaymentResource.java`:
  * **Auto-Allocation Strategy**: If `allocations` is omitted, the API fetches all unpaid invoices for the account and allocates the payment amount sequentially from oldest to newest.
  * **Overpayment & CBA**: Any excess cash (total payment amount minus total allocation) is attached to the first allocation. In Kill Bill, paying an amount exceeding the invoice's balance automatically converts the negative balance into a Customer Budget Account (CBA) credit.
  * **Zero Allocations Fallback**: If no unpaid invoices exist, the excess is attached to the latest invoice to create account-level credit.

---

## 3. Files Modified and Created

### Util Module
* `util/src/main/java/org/killbill/billing/util/tag/dao/SystemTags.java` (added `PAID_BY_EXTERNAL` definition)
* `util/src/main/resources/org/killbill/billing/util/tag/dao/TagSqlDao.sql.stg` (added database translation union mapping)
* `util/src/main/java/org/killbill/billing/util/tag/dao/TagModelDaoHelper.java` (added control tag checks)
* `util/src/main/java/org/killbill/billing/util/tag/DefaultTagDefinition.java` (restricted to ACCOUNT target type)
* `util/src/main/java/org/killbill/billing/util/tag/dao/DefaultTagDao.java` (enforced control tag and type validations)

### Payment Module
* `payment/src/main/java/org/killbill/billing/payment/invoice/InvoicePaymentControlPluginApi.java` (implemented automatic payment interception)
* `payment/src/test/java/org/killbill/billing/payment/invoice/TestInvoicePaymentControlPluginApiUnit.java` (created robust unit tests verifying the abortion flow)

### JAX-RS Module
* `jaxrs/src/main/java/org/killbill/billing/jaxrs/json/ExternalPaymentJson.java` (created request payload definition)
* `jaxrs/src/main/java/org/killbill/billing/jaxrs/resources/InvoicePaymentResource.java` (added `recordExternalPayment` POST endpoint)

---

## 4. Verification and Testing

### A. SpotBugs Verification
We verified the complete repository through SpotBugs check using the entire Maven reactor. The build completes with `BUILD SUCCESS` and zero bugs or errors.

### B. Unit Test Execution
We verified the `PAID_BY_EXTERNAL` dunning-prevention flow by running:
`mvn test -pl payment -Dtest=TestInvoicePaymentControlPluginApiUnit -Dnet.bytebuddy.experimental=true`

All tests compiled and passed successfully:
```
[INFO] Results:
[INFO] 
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```
The logs explicitly confirmed that the control hook detected the tag and successfully aborted the synthetic payment:
```
2026-07-18T00:25:59.292+0000 [main] INFO org.killbill.billing.payment.invoice.InvoicePaymentControlPluginApi - Aborting automatic external payment: account has PAID_BY_EXTERNAL tag, invoiceId='75b5ea80-28c2-44da-bdc4-35c49e0375e4'
```
