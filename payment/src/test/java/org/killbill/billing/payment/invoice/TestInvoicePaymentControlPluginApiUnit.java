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

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.customfield.CustomFieldInternalApi;
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
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.clock.Clock;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestInvoicePaymentControlPluginApiUnit extends PaymentTestSuiteNoDB {

    private InvoicePaymentControlPluginApi createInvoicePaymentControlApi() {
        return createInvoicePaymentControlApi(Mockito.mock(CustomFieldInternalApi.class));
    }

    private InvoicePaymentControlPluginApi createInvoicePaymentControlApi(final CustomFieldInternalApi customFieldApi) {
        final PaymentConfig paymentConfig = Mockito.mock(PaymentConfig.class);
        final InvoiceInternalApi internalApi = Mockito.mock(InvoiceInternalApi.class);
        final TagUserApi tagUserApi = Mockito.mock(TagUserApi.class);
        final PaymentDao paymentDao = Mockito.mock(PaymentDao.class);
        final InvoicePaymentControlDao invoicePaymentControlDao = Mockito.mock(InvoicePaymentControlDao.class);
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
                                                  accountInternalApi,
                                                  customFieldApi);
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

    // Regression test for https://github.com/killbill/killbill/issues/277:
    // when the invoice items all map to a single subscription that carries an
    // OVERRIDE_PAYMENT_METHOD_ID custom field, the override paymentMethodId is returned.
    @Test(groups = "fast")
    public void testPaymentMethodOverride_singleSubscription() {
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID overridePaymentMethodId = UUID.randomUUID();

        final InvoiceItem item1 = Mockito.mock(InvoiceItem.class);
        Mockito.when(item1.getSubscriptionId()).thenReturn(subscriptionId);
        Mockito.when(item1.getBundleId()).thenReturn(bundleId);
        final InvoiceItem item2 = Mockito.mock(InvoiceItem.class);
        Mockito.when(item2.getSubscriptionId()).thenReturn(subscriptionId);
        Mockito.when(item2.getBundleId()).thenReturn(bundleId);

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(java.util.List.of(item1, item2));

        final CustomField overrideField = Mockito.mock(CustomField.class);
        Mockito.when(overrideField.getFieldName()).thenReturn(InvoicePaymentControlPluginApi.OVERRIDE_PAYMENT_METHOD_FIELD_NAME);
        Mockito.when(overrideField.getFieldValue()).thenReturn(overridePaymentMethodId.toString());

        final CustomFieldInternalApi customFieldApi = Mockito.mock(CustomFieldInternalApi.class);
        Mockito.when(customFieldApi.getCustomFieldsForObject(Mockito.eq(subscriptionId), Mockito.eq(ObjectType.SUBSCRIPTION), Mockito.any(InternalTenantContext.class)))
               .thenReturn(java.util.List.of(overrideField));

        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi(customFieldApi);
        final UUID result = api.getPaymentMethodOverride(invoice, Mockito.mock(InternalTenantContext.class));
        Assert.assertEquals(result, overridePaymentMethodId);
    }

    // When the subscription has no override but the bundle does, the bundle override is honored.
    @Test(groups = "fast")
    public void testPaymentMethodOverride_bundleFallback() {
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID overridePaymentMethodId = UUID.randomUUID();

        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getSubscriptionId()).thenReturn(subscriptionId);
        Mockito.when(item.getBundleId()).thenReturn(bundleId);

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(java.util.List.of(item));

        final CustomField overrideField = Mockito.mock(CustomField.class);
        Mockito.when(overrideField.getFieldName()).thenReturn(InvoicePaymentControlPluginApi.OVERRIDE_PAYMENT_METHOD_FIELD_NAME);
        Mockito.when(overrideField.getFieldValue()).thenReturn(overridePaymentMethodId.toString());

        final CustomFieldInternalApi customFieldApi = Mockito.mock(CustomFieldInternalApi.class);
        // No subscription-level override:
        Mockito.when(customFieldApi.getCustomFieldsForObject(Mockito.eq(subscriptionId), Mockito.eq(ObjectType.SUBSCRIPTION), Mockito.any(InternalTenantContext.class)))
               .thenReturn(Collections.emptyList());
        // Bundle-level override present:
        Mockito.when(customFieldApi.getCustomFieldsForObject(Mockito.eq(bundleId), Mockito.eq(ObjectType.BUNDLE), Mockito.any(InternalTenantContext.class)))
               .thenReturn(java.util.List.of(overrideField));

        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi(customFieldApi);
        final UUID result = api.getPaymentMethodOverride(invoice, Mockito.mock(InternalTenantContext.class));
        Assert.assertEquals(result, overridePaymentMethodId);
    }

    // When the invoice contains items from multiple subscriptions and multiple bundles,
    // we cannot pick a single override safely => no override applied.
    @Test(groups = "fast")
    public void testPaymentMethodOverride_multipleSubscriptionsAndBundles_noOverride() {
        final InvoiceItem item1 = Mockito.mock(InvoiceItem.class);
        Mockito.when(item1.getSubscriptionId()).thenReturn(UUID.randomUUID());
        Mockito.when(item1.getBundleId()).thenReturn(UUID.randomUUID());
        final InvoiceItem item2 = Mockito.mock(InvoiceItem.class);
        Mockito.when(item2.getSubscriptionId()).thenReturn(UUID.randomUUID());
        Mockito.when(item2.getBundleId()).thenReturn(UUID.randomUUID());

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(java.util.List.of(item1, item2));

        final CustomFieldInternalApi customFieldApi = Mockito.mock(CustomFieldInternalApi.class);
        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi(customFieldApi);
        final UUID result = api.getPaymentMethodOverride(invoice, Mockito.mock(InternalTenantContext.class));
        Assert.assertNull(result);
        Mockito.verifyNoInteractions(customFieldApi);
    }

    // Malformed UUID value in the custom field must not blow up.
    @Test(groups = "fast")
    public void testPaymentMethodOverride_malformedValueIsIgnored() {
        final UUID subscriptionId = UUID.randomUUID();
        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getSubscriptionId()).thenReturn(subscriptionId);
        Mockito.when(item.getBundleId()).thenReturn(UUID.randomUUID());

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(java.util.List.of(item));

        final CustomField overrideField = Mockito.mock(CustomField.class);
        Mockito.when(overrideField.getFieldName()).thenReturn(InvoicePaymentControlPluginApi.OVERRIDE_PAYMENT_METHOD_FIELD_NAME);
        Mockito.when(overrideField.getFieldValue()).thenReturn("not-a-uuid");

        final CustomFieldInternalApi customFieldApi = Mockito.mock(CustomFieldInternalApi.class);
        Mockito.when(customFieldApi.getCustomFieldsForObject(Mockito.eq(subscriptionId), Mockito.eq(ObjectType.SUBSCRIPTION), Mockito.any(InternalTenantContext.class)))
               .thenReturn(java.util.List.of(overrideField));

        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi(customFieldApi);
        final UUID result = api.getPaymentMethodOverride(invoice, Mockito.mock(InternalTenantContext.class));
        Assert.assertNull(result);
    }
}
