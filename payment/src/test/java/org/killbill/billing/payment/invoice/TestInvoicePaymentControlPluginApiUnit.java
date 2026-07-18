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

import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
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
                                                  accountInternalApi);
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

    @Test(groups = "fast")
    public void testPriorCallWithPaidByExternalTag() throws Exception {
        final java.util.UUID invoiceId = java.util.UUID.randomUUID();
        final java.util.UUID accountId = java.util.UUID.randomUUID();
        final java.util.UUID paymentMethodId = java.util.UUID.randomUUID();

        final org.killbill.billing.util.config.definition.PaymentConfig paymentConfig = Mockito.mock(org.killbill.billing.util.config.definition.PaymentConfig.class);
        final org.killbill.billing.invoice.api.InvoiceInternalApi invoiceApi = Mockito.mock(org.killbill.billing.invoice.api.InvoiceInternalApi.class);
        final org.killbill.billing.util.api.TagUserApi tagUserApi = Mockito.mock(org.killbill.billing.util.api.TagUserApi.class);
        final org.killbill.billing.payment.dao.PaymentDao paymentDao = Mockito.mock(org.killbill.billing.payment.dao.PaymentDao.class);
        final org.killbill.billing.payment.invoice.dao.InvoicePaymentControlDao invoicePaymentControlDao = Mockito.mock(org.killbill.billing.payment.invoice.dao.InvoicePaymentControlDao.class);
        final org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler retryServiceScheduler = Mockito.mock(org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler.class);
        final org.killbill.billing.util.callcontext.InternalCallContextFactory contextFactory = Mockito.mock(org.killbill.billing.util.callcontext.InternalCallContextFactory.class);
        final org.killbill.billing.account.api.AccountInternalApi accountInternalApi = Mockito.mock(org.killbill.billing.account.api.AccountInternalApi.class);
        final org.killbill.clock.Clock clock = Mockito.mock(org.killbill.clock.Clock.class);

        final org.killbill.billing.payment.invoice.InvoicePaymentControlPluginApi api = new org.killbill.billing.payment.invoice.InvoicePaymentControlPluginApi(
                paymentConfig, invoiceApi, tagUserApi, paymentDao, invoicePaymentControlDao, retryServiceScheduler, contextFactory, clock, accountInternalApi
        );

        final org.killbill.billing.control.plugin.api.PaymentControlContext paymentControlContext = Mockito.mock(org.killbill.billing.control.plugin.api.PaymentControlContext.class);
        Mockito.when(paymentControlContext.getPaymentApiType()).thenReturn(org.killbill.billing.control.plugin.api.PaymentApiType.PAYMENT_TRANSACTION);
        Mockito.when(paymentControlContext.getTransactionType()).thenReturn(org.killbill.billing.payment.api.TransactionType.PURCHASE);
        Mockito.when(paymentControlContext.getAccountId()).thenReturn(accountId);
        Mockito.when(paymentControlContext.getPaymentMethodId()).thenReturn(paymentMethodId);
        Mockito.when(paymentControlContext.isApiPayment()).thenReturn(false);

        final org.killbill.billing.payment.api.PluginProperty invoiceProperty = new org.killbill.billing.payment.api.PluginProperty("IPCD_INVOICE_ID", invoiceId.toString(), false);
        final java.util.List<org.killbill.billing.payment.api.PluginProperty> pluginProperties = java.util.List.of(invoiceProperty);

        final org.killbill.billing.callcontext.InternalCallContext internalContext = Mockito.mock(org.killbill.billing.callcontext.InternalCallContext.class);
        Mockito.when(contextFactory.createInternalCallContext(Mockito.any(java.util.UUID.class), Mockito.any(org.killbill.billing.util.callcontext.CallContext.class))).thenReturn(internalContext);

        Mockito.when(invoiceApi.getInvoiceStatus(Mockito.eq(invoiceId), Mockito.eq(internalContext))).thenReturn(org.killbill.billing.invoice.api.InvoiceStatus.COMMITTED);

        final org.killbill.billing.invoice.api.Invoice invoice = Mockito.mock(org.killbill.billing.invoice.api.Invoice.class);
        Mockito.when(invoice.getId()).thenReturn(invoiceId);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getBalance()).thenReturn(java.math.BigDecimal.TEN);
        Mockito.when(invoiceApi.getInvoiceById(Mockito.eq(invoiceId), Mockito.eq(internalContext))).thenReturn(invoice);

        final org.killbill.billing.account.api.Account account = Mockito.mock(org.killbill.billing.account.api.Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(accountInternalApi.getAccountById(Mockito.eq(accountId), Mockito.eq(internalContext))).thenReturn(account);

        // Mock tags list to have PAID_BY_EXTERNAL
        final org.killbill.billing.util.tag.Tag externalTag = Mockito.mock(org.killbill.billing.util.tag.Tag.class);
        Mockito.when(externalTag.getTagDefinitionId()).thenReturn(org.killbill.billing.util.tag.dao.SystemTags.PAID_BY_EXTERNAL_TAG_DEFINITION_ID);
        final java.util.List<org.killbill.billing.util.tag.Tag> tags = java.util.List.of(externalTag);
        Mockito.when(tagUserApi.getTagsForAccount(Mockito.eq(accountId), Mockito.anyBoolean(), Mockito.eq(paymentControlContext))).thenReturn(tags);

        // Mock external payment method plugin
        final org.killbill.billing.payment.dao.PaymentMethodModelDao paymentMethod = Mockito.mock(org.killbill.billing.payment.dao.PaymentMethodModelDao.class);
        Mockito.when(paymentMethod.getPluginName()).thenReturn(org.killbill.billing.payment.provider.ExternalPaymentProviderPlugin.PLUGIN_NAME);
        Mockito.when(paymentDao.getPaymentMethod(Mockito.eq(paymentMethodId), Mockito.eq(internalContext))).thenReturn(paymentMethod);

        // Call priorCall and assert it is aborted
        final org.killbill.billing.control.plugin.api.PriorPaymentControlResult result = api.priorCall(paymentControlContext, pluginProperties);
        Assert.assertTrue(result.isAborted());
    }
}
