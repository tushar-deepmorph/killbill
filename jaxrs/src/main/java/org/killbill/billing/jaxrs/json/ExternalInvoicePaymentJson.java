/*
 * Copyright 2026 The Billing Project, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package org.killbill.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ExternalInvoicePaymentJson {

    public static class Allocation {
        private final UUID invoiceId;
        private final BigDecimal amount;

        @JsonCreator
        public Allocation(@JsonProperty("invoiceId") final UUID invoiceId, @JsonProperty("amount") final BigDecimal amount) {
            this.invoiceId = invoiceId;
            this.amount = amount;
        }

        public UUID getInvoiceId() { return invoiceId; }
        public BigDecimal getAmount() { return amount; }
    }

    private final UUID accountId;
    private final List<Allocation> allocations;
    private final DateTime effectiveDate;
    private final String paymentExternalKey;
    private final String transactionExternalKey;

    @JsonCreator
    public ExternalInvoicePaymentJson(@JsonProperty("accountId") final UUID accountId,
                                      @JsonProperty("allocations") final List<Allocation> allocations,
                                      @JsonProperty("effectiveDate") final DateTime effectiveDate,
                                      @JsonProperty("paymentExternalKey") final String paymentExternalKey,
                                      @JsonProperty("transactionExternalKey") final String transactionExternalKey) {
        this.accountId = accountId;
        this.allocations = allocations;
        this.effectiveDate = effectiveDate;
        this.paymentExternalKey = paymentExternalKey;
        this.transactionExternalKey = transactionExternalKey;
    }

    public UUID getAccountId() { return accountId; }
    public List<Allocation> getAllocations() { return allocations; }
    public DateTime getEffectiveDate() { return effectiveDate; }
    public String getPaymentExternalKey() { return paymentExternalKey; }
    public String getTransactionExternalKey() { return transactionExternalKey; }
}
