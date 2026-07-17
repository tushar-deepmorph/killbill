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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.CacheLoaderArgument;
import org.killbill.billing.util.cache.NoOpCacheController;
import org.testng.Assert;
import org.testng.annotations.Test;

import jakarta.inject.Provider;
import java.util.HashMap;
import java.util.Map;

public class TestTenantAwareClock {

    @Test(groups = "fast")
    public void testTenantAwareClockWithNoContext() {
        InternalTenantContext.clearContext();

        final TenantAwareClock clock = new TenantAwareClock(null);
        final DateTime now = clock.getUTCNow();
        Assert.assertNotNull(now);
    }

    @Test(groups = "fast")
    public void testTenantAwareClockWithContextAndDelta() {
        final InternalTenantContext context = new InternalTenantContext(42L);
        InternalTenantContext.setContext(context);

        final Map<String, String> cacheMap = new HashMap<>();
        // Set clock delta to 10 days in milliseconds (10 * 24 * 3600 * 1000 = 864000000)
        cacheMap.put(TenantAwareClock.CLOCK_DELTA_KEY + "::42", "864000000");

        final CacheController<String, String> mockCache = new NoOpCacheController<String, String>(null) {
            @Override
            public String get(final String key, final CacheLoaderArgument cacheLoaderArgument) {
                return cacheMap.get(key);
            }

            @Override
            public boolean isKeyInCache(final String key) {
                return cacheMap.containsKey(key);
            }
        };

        final CacheControllerDispatcher mockDispatcher = new CacheControllerDispatcher() {
            @SuppressWarnings("unchecked")
            @Override
            public <K, V> CacheController<K, V> getCacheController(final CacheType cacheType) {
                if (cacheType == CacheType.TENANT_KV) {
                    return (CacheController<K, V>) mockCache;
                }
                return null;
            }
        };

        final Provider<CacheControllerDispatcher> provider = new Provider<CacheControllerDispatcher>() {
            @Override
            public CacheControllerDispatcher get() {
                return mockDispatcher;
            }
        };

        final TenantAwareClock clock = new TenantAwareClock(provider);
        final DateTime originalNow = new DateTime(DateTimeZone.UTC);
        final DateTime shiftedNow = clock.getUTCNow();

        final long diff = shiftedNow.getMillis() - originalNow.getMillis();
        // Since we shifted by 10 days, the diff should be around 10 days
        Assert.assertTrue(diff >= 863900000L && diff <= 864100000L, "Shifted now should be ~10 days after original now. Diff: " + diff);

        InternalTenantContext.clearContext();
    }
}
