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

package org.killbill.billing.util.glue;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.CacheLoaderArgument;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TenantAwareClock implements Clock {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareClock.class);

    public static final String CLOCK_DELTA_KEY = "PLUGIN_CONFIG_clockDelta";

    private final Clock delegate;
    private final Provider<CacheControllerDispatcher> cacheControllerDispatcherProvider;

    @Inject
    public TenantAwareClock(final Provider<CacheControllerDispatcher> cacheControllerDispatcherProvider) {
        this.delegate = new DefaultClock();
        this.cacheControllerDispatcherProvider = cacheControllerDispatcherProvider;
    }

    private long getDeltaMs() {
        final InternalTenantContext context = InternalTenantContext.getContext();
        if (context == null || context.getTenantRecordId() == null || context.getTenantRecordId() == 0L) {
            return 0L;
        }

        try {
            final CacheControllerDispatcher dispatcher = cacheControllerDispatcherProvider.get();
            if (dispatcher == null) {
                return 0L;
            }
            final CacheController<String, String> cache = dispatcher.getCacheController(CacheType.TENANT_KV);
            if (cache == null) {
                return 0L;
            }

            final String cacheKey = CLOCK_DELTA_KEY + "::" + context.getTenantRecordId();
            final String cachedValue = cache.get(cacheKey, new CacheLoaderArgument(ObjectType.TENANT_KVS));
            if (cachedValue != null && !cachedValue.equals("0") && !cachedValue.isEmpty()) {
                if (cachedValue.equals(org.killbill.billing.util.cache.BaseCacheLoader.EMPTY_VALUE_PLACEHOLDER)) {
                    return 0L;
                }
                return Long.parseLong(cachedValue);
            }
        } catch (final Exception e) {
            log.warn("Failed to retrieve per-tenant clock delta", e);
        }
        return 0L;
    }

    @Override
    public DateTime getUTCNow() {
        final DateTime realNow = delegate.getUTCNow();
        final long delta = getDeltaMs();
        if (delta != 0) {
            return realNow.plusMillis((int) delta);
        }
        return realNow;
    }

    @Override
    public LocalDate getUTCToday() {
        return getToday(DateTimeZone.UTC);
    }

    @Override
    public LocalDate getToday(final DateTimeZone timeZone) {
        return getNow(timeZone).toLocalDate();
    }

    @Override
    public DateTime getNow(final DateTimeZone timeZone) {
        final DateTime realNow = delegate.getNow(timeZone);
        final long delta = getDeltaMs();
        if (delta != 0) {
            return realNow.plusMillis((int) delta);
        }
        return realNow;
    }
}
