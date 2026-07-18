/*
 * Copyright 2026 The Billing Project, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;

public class DefaultExternalInvoicePaymentApi implements ExternalInvoicePaymentApi {

    private final InvoicePaymentInternalApi invoicePaymentInternalApi;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultExternalInvoicePaymentApi(final InvoicePaymentInternalApi invoicePaymentInternalApi,
                                            final InternalCallContextFactory internalCallContextFactory) {
        this.invoicePaymentInternalApi = invoicePaymentInternalApi;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public Payment recordExternalPayment(final Account account,
                                         final Map<UUID, BigDecimal> invoiceAllocations,
                                         final DateTime effectiveDate,
                                         final String paymentExternalKey,
                                         final String paymentTransactionExternalKey,
                                         final Iterable<PluginProperty> properties,
                                         final CallContext context) throws PaymentApiException {
        return invoicePaymentInternalApi.createPurchaseForInvoicePayments(true, account, invoiceAllocations, effectiveDate,
                                                                          paymentExternalKey, paymentTransactionExternalKey,
                                                                          properties, new PaymentOptions() {
                                                                              @Override
                                                                              public boolean isExternalPayment() { return true; }

                                                                              @Override
                                                                              public java.util.List<String> getPaymentControlPluginNames() { return Collections.emptyList(); }
                                                                          },
                                                                          internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }
}
