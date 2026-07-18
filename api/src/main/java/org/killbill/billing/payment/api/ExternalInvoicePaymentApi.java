/*
 * Copyright 2026 The Billing Project, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.util.callcontext.CallContext;

public interface ExternalInvoicePaymentApi {

    Payment recordExternalPayment(Account account,
                                  Map<UUID, BigDecimal> invoiceAllocations,
                                  DateTime effectiveDate,
                                  String paymentExternalKey,
                                  String paymentTransactionExternalKey,
                                  Iterable<PluginProperty> properties,
                                  CallContext context) throws PaymentApiException;
}
