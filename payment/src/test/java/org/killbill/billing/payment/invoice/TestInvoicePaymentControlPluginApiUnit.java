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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.control.plugin.api.PaymentApiType;
import org.killbill.billing.control.plugin.api.PaymentControlContext;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.invoice.dao.InvoicePaymentControlDao;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.dao.SystemTags;
import org.killbill.clock.Clock;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestInvoicePaymentControlPluginApiUnit extends PaymentTestSuiteNoDB {

    private TagUserApi tagUserApi;
    private InvoiceInternalApi invoiceInternalApi;
    private AccountInternalApi accountInternalApi;
    private InternalCallContextFactory contextFactory;

    private InvoicePaymentControlPluginApi createInvoicePaymentControlApi() {
        final PaymentConfig paymentConfig = Mockito.mock(PaymentConfig.class);
        invoiceInternalApi = Mockito.mock(InvoiceInternalApi.class);
        tagUserApi = Mockito.mock(TagUserApi.class);
        final PaymentDao paymentDao = Mockito.mock(PaymentDao.class);
        final InvoicePaymentControlDao invoicePaymentControlDao = Mockito.mock(InvoicePaymentControlDao.class);
        final RetryServiceScheduler retryServiceScheduler = Mockito.mock(RetryServiceScheduler.class);
        contextFactory = Mockito.mock(InternalCallContextFactory.class);
        accountInternalApi = Mockito.mock(AccountInternalApi.class);
        final Clock clock = Mockito.mock(Clock.class);

        return new InvoicePaymentControlPluginApi(paymentConfig,
                                                  invoiceInternalApi,
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
    public void testAutomaticPaymentAbortedForPaidByExternalAccount() throws Exception {
        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi();

        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        // Automatic (non-API) payment on an account flagged PAID_BY_EXTERNAL must be aborted, leaving the invoice unpaid.
        final PaymentControlContext ctx = setupPurchase(accountId, invoiceId, false, null, true);

        final PriorPaymentControlResult res = api.priorCall(ctx, List.of(new PluginProperty("IPCD_INVOICE_ID", invoiceId.toString(), Boolean.FALSE)));
        Assert.assertTrue(res.isAborted());
    }

    @Test(groups = "fast")
    public void testApiPaymentNotAbortedForPaidByExternalAccount() throws Exception {
        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi();

        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        // The biller-recorded external payment is an API payment: it must NOT be aborted by the PAID_BY_EXTERNAL guard.
        final PaymentControlContext ctx = setupPurchase(accountId, invoiceId, true, UUID.randomUUID(), true);
        Mockito.when(invoiceInternalApi.getInvoicePaymentsByInvoice(Mockito.eq(invoiceId), Mockito.any())).thenReturn(Collections.emptyList());

        final PriorPaymentControlResult res = api.priorCall(ctx, List.of(new PluginProperty("IPCD_INVOICE_ID", invoiceId.toString(), Boolean.FALSE)));
        Assert.assertFalse(res.isAborted());
        Assert.assertEquals(res.getAdjustedAmount().compareTo(new BigDecimal("100")), 0);
    }

    private PaymentControlContext setupPurchase(final UUID accountId,
                                                final UUID invoiceId,
                                                final boolean isApiPayment,
                                                final UUID paymentMethodId,
                                                final boolean paidByExternal) throws Exception {
        final PaymentControlContext ctx = Mockito.mock(PaymentControlContext.class);
        Mockito.when(ctx.getTransactionType()).thenReturn(TransactionType.PURCHASE);
        Mockito.when(ctx.getPaymentApiType()).thenReturn(PaymentApiType.PAYMENT_TRANSACTION);
        Mockito.when(ctx.getAccountId()).thenReturn(accountId);
        Mockito.when(ctx.isApiPayment()).thenReturn(isApiPayment);
        Mockito.when(ctx.getAmount()).thenReturn(new BigDecimal("100"));
        Mockito.when(ctx.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(ctx.getPaymentMethodId()).thenReturn(paymentMethodId);
        Mockito.when(ctx.getPaymentId()).thenReturn(UUID.randomUUID());
        Mockito.when(ctx.getAttemptPaymentId()).thenReturn(UUID.randomUUID());

        final InternalCallContext internalContext = Mockito.mock(InternalCallContext.class);
        Mockito.when(contextFactory.createInternalCallContext(Mockito.eq(accountId), Mockito.eq(ctx))).thenReturn(internalContext);

        Mockito.when(invoiceInternalApi.getInvoiceStatus(Mockito.eq(invoiceId), Mockito.any())).thenReturn(InvoiceStatus.COMMITTED);
        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getId()).thenReturn(invoiceId);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getBalance()).thenReturn(new BigDecimal("100"));
        Mockito.when(invoice.getParentAccountId()).thenReturn(null);
        Mockito.when(invoice.getPayments()).thenReturn(Collections.emptyList());
        Mockito.when(invoiceInternalApi.getInvoiceById(Mockito.eq(invoiceId), Mockito.any())).thenReturn(invoice);

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getParentAccountId()).thenReturn(null);
        Mockito.when(accountInternalApi.getAccountById(Mockito.eq(accountId), Mockito.any())).thenReturn(account);

        final List<Tag> tags = new ArrayList<>();
        if (paidByExternal) {
            final Tag tag = Mockito.mock(Tag.class);
            Mockito.when(tag.getTagDefinitionId()).thenReturn(SystemTags.PAID_BY_EXTERNAL_TAG_DEFINITION_ID);
            tags.add(tag);
        }
        Mockito.when(tagUserApi.getTagsForAccount(Mockito.eq(accountId), Mockito.eq(false), Mockito.any())).thenReturn(tags);

        return ctx;
    }
}
