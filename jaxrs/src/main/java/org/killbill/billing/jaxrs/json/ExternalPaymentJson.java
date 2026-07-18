/*
 * Copyright 2020-2024 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ExternalPayment")
public class ExternalPaymentJson {

    private final UUID accountId;
    private final BigDecimal amount;
    private final Currency currency;
    private final UUID paymentMethodId;
    private final String paymentExternalKey;
    private final String transactionExternalKey;
    private final DateTime effectiveDate;
    private final Map<UUID, BigDecimal> allocations;

    @JsonCreator
    public ExternalPaymentJson(@JsonProperty("accountId") final UUID accountId,
                               @JsonProperty("amount") final BigDecimal amount,
                               @JsonProperty("currency") final Currency currency,
                               @JsonProperty("paymentMethodId") @Nullable final UUID paymentMethodId,
                               @JsonProperty("paymentExternalKey") @Nullable final String paymentExternalKey,
                               @JsonProperty("transactionExternalKey") @Nullable final String transactionExternalKey,
                               @JsonProperty("effectiveDate") @Nullable final DateTime effectiveDate,
                               @JsonProperty("allocations") @Nullable final Map<UUID, BigDecimal> allocations) {
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethodId = paymentMethodId;
        this.paymentExternalKey = paymentExternalKey;
        this.transactionExternalKey = transactionExternalKey;
        this.effectiveDate = effectiveDate;
        this.allocations = allocations;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    @Nullable
    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    @Nullable
    public String getPaymentExternalKey() {
        return paymentExternalKey;
    }

    @Nullable
    public String getTransactionExternalKey() {
        return transactionExternalKey;
    }

    @Nullable
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Nullable
    public Map<UUID, BigDecimal> getAllocations() {
        return allocations;
    }
}
