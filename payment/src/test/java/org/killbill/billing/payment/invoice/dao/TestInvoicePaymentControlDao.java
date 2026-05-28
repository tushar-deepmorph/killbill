/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestInvoicePaymentControlDao extends PaymentTestSuiteWithEmbeddedDB {

    private InvoicePaymentControlDao dao;

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        dao = new InvoicePaymentControlDao(dbi);
    }

    @Test(groups = "slow")
    public void testPluginAutoPayOffSimple() {
        final UUID accountId = UUID.randomUUID();
        final UUID attemptId = UUID.randomUUID();
        final UUID paymentId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal("13.33");
        final DateTime utcNow = clock.getUTCNow();
        final PluginAutoPayOffModelDao entry1 = new PluginAutoPayOffModelDao(attemptId, "key1", "tkey1", accountId, "XXX", paymentId, amount, Currency.USD, "lulu", utcNow);
        dao.insertAutoPayOff(entry1);

        final List<PluginAutoPayOffModelDao> entries = dao.getAutoPayOffEntry(accountId);
        assertEquals(entries.size(), 1);
        assertEquals(entries.get(0).getPaymentExternalKey(), "key1");
        assertEquals(entries.get(0).getTransactionExternalKey(), "tkey1");
        assertEquals(entries.get(0).getAccountId(), accountId);
        assertEquals(entries.get(0).getPluginName(), "XXX");
        assertEquals(entries.get(0).getPaymentId(), paymentId);
        assertEquals(entries.get(0).getAmount().compareTo(amount), 0);
        assertEquals(entries.get(0).getCurrency(), Currency.USD);
        assertEquals(entries.get(0).getCreatedBy(), "lulu");
        assertEquals(entries.get(0).getCreatedDate().compareTo(utcNow), 0);
    }

    @Test(groups = "slow")
    public void testPluginAutoPayOffMutlitpleEntries() {

        final UUID accountId = UUID.randomUUID();
        final UUID attemptId = UUID.randomUUID();
        final UUID paymentId1 = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal("13.33");
        final DateTime utcNow = clock.getUTCNow();
        final PluginAutoPayOffModelDao entry1 = new PluginAutoPayOffModelDao(attemptId, "key1", "tkey1", accountId, "XXX", paymentId1, amount, Currency.USD, "lulu", utcNow);
        dao.insertAutoPayOff(entry1);

        final UUID paymentId2 = UUID.randomUUID();
        final PluginAutoPayOffModelDao entry2 = new PluginAutoPayOffModelDao(attemptId, "key2", "tkey2", accountId, "XXX", paymentId2, amount, Currency.USD, "lulu", utcNow);
        dao.insertAutoPayOff(entry2);

        final List<PluginAutoPayOffModelDao> entries = dao.getAutoPayOffEntry(accountId);
        assertEquals(entries.size(), 2);
    }

    @Test(groups = "slow")
    public void testPluginAutoPayOffNoEntries() {

        final UUID accountId = UUID.randomUUID();
        final UUID paymentId1 = UUID.randomUUID();
        final UUID attemptId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal("13.33");
        final DateTime utcNow = clock.getUTCNow();
        final PluginAutoPayOffModelDao entry1 = new PluginAutoPayOffModelDao(attemptId, "key1", "tkey1", accountId, "XXX", paymentId1, amount, Currency.USD, "lulu", utcNow);
        dao.insertAutoPayOff(entry1);

        final List<PluginAutoPayOffModelDao> entries = dao.getAutoPayOffEntry(UUID.randomUUID());
        assertEquals(entries.size(), 0);
    }

    // -- Per-subscription / per-bundle payment method overrides (issue #277) --

    @Test(groups = "slow")
    public void testPaymentMethodOverrideSetAndGet() {
        final UUID accountId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID paymentMethodId = UUID.randomUUID();
        final DateTime utcNow = clock.getUTCNow();

        dao.setPaymentMethodOverride(new PluginPaymentMethodOverrideModelDao(accountId, ObjectType.SUBSCRIPTION, subscriptionId, paymentMethodId, "lulu", utcNow));

        assertEquals(dao.getPaymentMethodOverride(accountId, ObjectType.SUBSCRIPTION, subscriptionId), paymentMethodId);
        // Different object id => no override
        assertNull(dao.getPaymentMethodOverride(accountId, ObjectType.SUBSCRIPTION, UUID.randomUUID()));
        // Same id but different object type => no override (type is part of the key)
        assertNull(dao.getPaymentMethodOverride(accountId, ObjectType.BUNDLE, subscriptionId));
        // Different account => no override
        assertNull(dao.getPaymentMethodOverride(UUID.randomUUID(), ObjectType.SUBSCRIPTION, subscriptionId));
    }

    @Test(groups = "slow")
    public void testPaymentMethodOverrideIsReplacedOnReset() {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID firstPaymentMethodId = UUID.randomUUID();
        final UUID secondPaymentMethodId = UUID.randomUUID();
        final DateTime utcNow = clock.getUTCNow();

        dao.setPaymentMethodOverride(new PluginPaymentMethodOverrideModelDao(accountId, ObjectType.BUNDLE, bundleId, firstPaymentMethodId, "lulu", utcNow));
        dao.setPaymentMethodOverride(new PluginPaymentMethodOverrideModelDao(accountId, ObjectType.BUNDLE, bundleId, secondPaymentMethodId, "lulu", utcNow));

        // The most-recent override wins; the previous row has been soft-deleted.
        assertEquals(dao.getPaymentMethodOverride(accountId, ObjectType.BUNDLE, bundleId), secondPaymentMethodId);
    }

    @Test(groups = "slow")
    public void testPaymentMethodOverrideRemove() {
        final UUID accountId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID paymentMethodId = UUID.randomUUID();
        final DateTime utcNow = clock.getUTCNow();

        dao.setPaymentMethodOverride(new PluginPaymentMethodOverrideModelDao(accountId, ObjectType.SUBSCRIPTION, subscriptionId, paymentMethodId, "lulu", utcNow));
        assertEquals(dao.getPaymentMethodOverride(accountId, ObjectType.SUBSCRIPTION, subscriptionId), paymentMethodId);

        dao.removePaymentMethodOverride(accountId, ObjectType.SUBSCRIPTION, subscriptionId);
        assertNull(dao.getPaymentMethodOverride(accountId, ObjectType.SUBSCRIPTION, subscriptionId));
    }

    @Test(groups = "slow")
    public void testPaymentMethodOverrideNoEntry() {
        assertNull(dao.getPaymentMethodOverride(UUID.randomUUID(), ObjectType.SUBSCRIPTION, UUID.randomUUID()));
    }
}
