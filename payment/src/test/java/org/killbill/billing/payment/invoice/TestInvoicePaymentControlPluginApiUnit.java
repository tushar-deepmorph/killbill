/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.payment.invoice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.invoice.dao.InvoicePaymentControlDao;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.clock.Clock;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestInvoicePaymentControlPluginApiUnit extends PaymentTestSuiteNoDB {

    private InvoicePaymentControlPluginApi createInvoicePaymentControlApi() {
        return createInvoicePaymentControlApi(Mockito.mock(InvoicePaymentControlDao.class));
    }

    private InvoicePaymentControlPluginApi createInvoicePaymentControlApi(final InvoicePaymentControlDao invoicePaymentControlDao) {
        final PaymentConfig paymentConfig = Mockito.mock(PaymentConfig.class);
        final InvoiceInternalApi internalApi = Mockito.mock(InvoiceInternalApi.class);
        final TagUserApi tagUserApi = Mockito.mock(TagUserApi.class);
        final PaymentDao paymentDao = Mockito.mock(PaymentDao.class);
        final RetryServiceScheduler retryServiceScheduler = Mockito.mock(RetryServiceScheduler.class);
        final InternalCallContextFactory contextFactory = Mockito.mock(InternalCallContextFactory.class);
        final AccountInternalApi accountInternalApi = Mockito.mock(AccountInternalApi.class);
        final Clock clock = Mockito.mock(Clock.class);

        return new InvoicePaymentControlPluginApi(paymentConfig,
                                                  internalApi,
                                                  tagUserApi,
                                                  paymentDao,
                                                  invoicePaymentControlDao,
                                                  retryServiceScheduler,
                                                  contextFactory,
                                                  clock,
                                                  accountInternalApi);
    }

    private InvoiceItem mockInvoiceItem(final UUID subscriptionId, final UUID bundleId) {
        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getSubscriptionId()).thenReturn(subscriptionId);
        Mockito.when(item.getBundleId()).thenReturn(bundleId);
        return item;
    }

    private Invoice mockInvoice(final UUID accountId, final List<InvoiceItem> items) {
        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(items);
        return invoice;
    }

    private Collection<PaymentTransactionModelDao> createPaymentTransactionModelDao(final TransactionStatus... modelDaoAvailableStatuses) {
        final Collection<PaymentTransactionModelDao> result = new ArrayList<>();
        for (final TransactionStatus status : modelDaoAvailableStatuses) {
            final PaymentTransactionModelDao modelDao = Mockito.mock(PaymentTransactionModelDao.class);
            Mockito.when(modelDao.getTransactionStatus()).thenReturn(status);
            result.add(modelDao);
        }
        return result;
    }

    @Test(groups = "fast")
    public void testGetNumberAttemptsInState() {
        final Collection<PaymentTransactionModelDao> modelDao = createPaymentTransactionModelDao(
                TransactionStatus.SUCCESS,
                TransactionStatus.SUCCESS,
                TransactionStatus.PAYMENT_FAILURE,
                TransactionStatus.PENDING,
                TransactionStatus.PAYMENT_FAILURE,
                TransactionStatus.PAYMENT_SYSTEM_OFF,
                TransactionStatus.PENDING,
                TransactionStatus.SUCCESS);

        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi();

        int result = api.getNumberAttemptsInState(modelDao, TransactionStatus.SUCCESS);
        Assert.assertEquals(result, 3);

        result = api.getNumberAttemptsInState(modelDao, TransactionStatus.PENDING, TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(result, 4);

        result = api.getNumberAttemptsInState(modelDao, TransactionStatus.PAYMENT_SYSTEM_OFF);
        Assert.assertEquals(result, 1);

        result = api.getNumberAttemptsInState(modelDao, TransactionStatus.UNKNOWN);
        Assert.assertEquals(result, 0);

        result = api.getNumberAttemptsInState(Collections.emptyList(), TransactionStatus.SUCCESS);
        Assert.assertEquals(result, 0);
    }

    // -- Regression coverage for issue #277: override paymentMethod per subscription/bundle --

    @Test(groups = "fast")
    public void testResolvePaymentMethodOverride_singleSubscriptionOverride() {
        final UUID accountId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID overridePaymentMethodId = UUID.randomUUID();

        final InvoicePaymentControlDao dao = Mockito.mock(InvoicePaymentControlDao.class);
        Mockito.when(dao.getPaymentMethodOverride(accountId, ObjectType.SUBSCRIPTION, subscriptionId))
               .thenReturn(overridePaymentMethodId);

        final Invoice invoice = mockInvoice(accountId, List.of(mockInvoiceItem(subscriptionId, bundleId)));
        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi(dao);

        Assert.assertEquals(api.resolvePaymentMethodOverride(invoice), overridePaymentMethodId);
    }

    @Test(groups = "fast")
    public void testResolvePaymentMethodOverride_bundleFallbackWhenNoSubscriptionOverride() {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID overridePaymentMethodId = UUID.randomUUID();

        final InvoicePaymentControlDao dao = Mockito.mock(InvoicePaymentControlDao.class);
        Mockito.when(dao.getPaymentMethodOverride(accountId, ObjectType.SUBSCRIPTION, subscriptionId)).thenReturn(null);
        Mockito.when(dao.getPaymentMethodOverride(accountId, ObjectType.BUNDLE, bundleId)).thenReturn(overridePaymentMethodId);

        final Invoice invoice = mockInvoice(accountId, List.of(mockInvoiceItem(subscriptionId, bundleId)));
        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi(dao);

        Assert.assertEquals(api.resolvePaymentMethodOverride(invoice), overridePaymentMethodId);
    }

    @Test(groups = "fast")
    public void testResolvePaymentMethodOverride_unifiedAcrossItems() {
        final UUID accountId = UUID.randomUUID();
        final UUID subA = UUID.randomUUID();
        final UUID subB = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID overridePaymentMethodId = UUID.randomUUID();

        final InvoicePaymentControlDao dao = Mockito.mock(InvoicePaymentControlDao.class);
        Mockito.when(dao.getPaymentMethodOverride(accountId, ObjectType.SUBSCRIPTION, subA)).thenReturn(overridePaymentMethodId);
        Mockito.when(dao.getPaymentMethodOverride(accountId, ObjectType.SUBSCRIPTION, subB)).thenReturn(overridePaymentMethodId);

        final Invoice invoice = mockInvoice(accountId, List.of(mockInvoiceItem(subA, bundleId),
                                                               mockInvoiceItem(subB, bundleId)));
        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi(dao);

        Assert.assertEquals(api.resolvePaymentMethodOverride(invoice), overridePaymentMethodId);
    }

    @Test(groups = "fast")
    public void testResolvePaymentMethodOverride_conflictingOverridesFallBack() {
        final UUID accountId = UUID.randomUUID();
        final UUID subA = UUID.randomUUID();
        final UUID subB = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID pmA = UUID.randomUUID();
        final UUID pmB = UUID.randomUUID();

        final InvoicePaymentControlDao dao = Mockito.mock(InvoicePaymentControlDao.class);
        Mockito.when(dao.getPaymentMethodOverride(accountId, ObjectType.SUBSCRIPTION, subA)).thenReturn(pmA);
        Mockito.when(dao.getPaymentMethodOverride(accountId, ObjectType.SUBSCRIPTION, subB)).thenReturn(pmB);

        final Invoice invoice = mockInvoice(accountId, List.of(mockInvoiceItem(subA, bundleId),
                                                               mockInvoiceItem(subB, bundleId)));
        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi(dao);

        Assert.assertNull(api.resolvePaymentMethodOverride(invoice),
                          "Conflicting overrides on the same invoice must fall back to the account default");
    }

    @Test(groups = "fast")
    public void testResolvePaymentMethodOverride_partialCoverageFallBack() {
        // Override defined for sub A but not for sub B on the same invoice => fall back so we
        // never split a single invoice across two payment methods.
        final UUID accountId = UUID.randomUUID();
        final UUID subA = UUID.randomUUID();
        final UUID subB = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID overridePaymentMethodId = UUID.randomUUID();

        final InvoicePaymentControlDao dao = Mockito.mock(InvoicePaymentControlDao.class);
        Mockito.when(dao.getPaymentMethodOverride(accountId, ObjectType.SUBSCRIPTION, subA)).thenReturn(overridePaymentMethodId);
        Mockito.when(dao.getPaymentMethodOverride(accountId, ObjectType.SUBSCRIPTION, subB)).thenReturn(null);
        Mockito.when(dao.getPaymentMethodOverride(accountId, ObjectType.BUNDLE, bundleId)).thenReturn(null);

        final Invoice invoice = mockInvoice(accountId, List.of(mockInvoiceItem(subA, bundleId),
                                                               mockInvoiceItem(subB, bundleId)));
        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi(dao);

        Assert.assertNull(api.resolvePaymentMethodOverride(invoice));
    }

    @Test(groups = "fast")
    public void testResolvePaymentMethodOverride_noOverridesRegistered() {
        final UUID accountId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();

        final InvoicePaymentControlDao dao = Mockito.mock(InvoicePaymentControlDao.class);
        Mockito.when(dao.getPaymentMethodOverride(Mockito.eq(accountId), Mockito.any(ObjectType.class), Mockito.any(UUID.class)))
               .thenReturn(null);

        final Invoice invoice = mockInvoice(accountId, List.of(mockInvoiceItem(subscriptionId, bundleId)));
        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi(dao);

        Assert.assertNull(api.resolvePaymentMethodOverride(invoice));
    }

    @Test(groups = "fast")
    public void testResolvePaymentMethodOverride_nullInvoiceIsSafe() {
        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi();
        Assert.assertNull(api.resolvePaymentMethodOverride(null));
    }
}
