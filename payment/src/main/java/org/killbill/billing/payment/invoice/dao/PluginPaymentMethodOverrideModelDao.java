/*
 * Copyright 2014-2024 The Billing Project, LLC
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

package org.killbill.billing.payment.invoice.dao;

import java.util.Objects;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;

/**
 * Maps a SUBSCRIPTION or BUNDLE identifier to the payment method that should
 * be used when paying invoices generated for that object. Created in support
 * of <a href="https://github.com/killbill/killbill/issues/277">#277</a>:
 * ability to override paymentMethod per subscription/bundle.
 */
public class PluginPaymentMethodOverrideModelDao {

    private Long recordId;
    private UUID accountId;
    private ObjectType paymentObjectType;
    private UUID paymentObjectId;
    private UUID paymentMethodId;
    private String createdBy;
    private DateTime createdDate;

    public PluginPaymentMethodOverrideModelDao() { /* For the DAO mapper */
    }

    public PluginPaymentMethodOverrideModelDao(final UUID accountId,
                                               final ObjectType paymentObjectType,
                                               final UUID paymentObjectId,
                                               final UUID paymentMethodId,
                                               final String createdBy,
                                               final DateTime createdDate) {
        this(-1L, accountId, paymentObjectType, paymentObjectId, paymentMethodId, createdBy, createdDate);
    }

    public PluginPaymentMethodOverrideModelDao(final Long recordId,
                                               final UUID accountId,
                                               final ObjectType paymentObjectType,
                                               final UUID paymentObjectId,
                                               final UUID paymentMethodId,
                                               final String createdBy,
                                               final DateTime createdDate) {
        this.recordId = recordId;
        this.accountId = accountId;
        this.paymentObjectType = paymentObjectType;
        this.paymentObjectId = paymentObjectId;
        this.paymentMethodId = paymentMethodId;
        this.createdBy = createdBy;
        this.createdDate = createdDate;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(final Long recordId) {
        this.recordId = recordId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(final UUID accountId) {
        this.accountId = accountId;
    }

    public ObjectType getPaymentObjectType() {
        return paymentObjectType;
    }

    public void setPaymentObjectType(final ObjectType paymentObjectType) {
        this.paymentObjectType = paymentObjectType;
    }

    public UUID getPaymentObjectId() {
        return paymentObjectId;
    }

    public void setPaymentObjectId(final UUID paymentObjectId) {
        this.paymentObjectId = paymentObjectId;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public void setPaymentMethodId(final UUID paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(final DateTime createdDate) {
        this.createdDate = createdDate;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PluginPaymentMethodOverrideModelDao)) {
            return false;
        }
        final PluginPaymentMethodOverrideModelDao that = (PluginPaymentMethodOverrideModelDao) o;
        return Objects.equals(accountId, that.accountId)
                && paymentObjectType == that.paymentObjectType
                && Objects.equals(paymentObjectId, that.paymentObjectId)
                && Objects.equals(paymentMethodId, that.paymentMethodId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, paymentObjectType, paymentObjectId, paymentMethodId);
    }

    @Override
    public String toString() {
        return "PluginPaymentMethodOverrideModelDao{" +
                "recordId=" + recordId +
                ", accountId=" + accountId +
                ", paymentObjectType=" + paymentObjectType +
                ", paymentObjectId=" + paymentObjectId +
                ", paymentMethodId=" + paymentMethodId +
                ", createdBy='" + createdBy + '\'' +
                ", createdDate=" + createdDate +
                '}';
    }
}
