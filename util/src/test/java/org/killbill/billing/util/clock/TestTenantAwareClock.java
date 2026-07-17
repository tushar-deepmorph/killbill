/*
 * Copyright 2010-2024 The Billing Project, LLC
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

package org.killbill.billing.util.clock;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.util.config.tenant.PerTenantConfig;
import org.killbill.clock.Clock;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class TestTenantAwareClock {

    private static final long TENANT_RECORD_ID = 17L;
    private static final DateTime BASE = new DateTime("2020-01-01T08:00:00.000Z");

    @AfterMethod(groups = "fast")
    public void tearDown() {
        TenantClockContextHolder.clear();
    }

    @Test(groups = "fast")
    public void testNoTenantInContextBehavesLikeDelegate() {
        // A delta is configured, but with no tenant in context it must not be applied.
        final TenantAwareClock clock = clockWithDelta(String.valueOf(24L * 3600 * 1000));

        Assert.assertEquals(clock.getUTCNow(), BASE);
        Assert.assertEquals(clock.getUTCToday(), new LocalDate(BASE, DateTimeZone.UTC));
    }

    @Test(groups = "fast")
    public void testNoDeltaConfiguredBehavesLikeDelegate() {
        final TenantAwareClock clock = clockWithDelta(null);
        TenantClockContextHolder.setTenantRecordId(TENANT_RECORD_ID);

        Assert.assertEquals(clock.getUTCNow(), BASE);
        Assert.assertEquals(clock.getToday(DateTimeZone.UTC), new LocalDate(BASE, DateTimeZone.UTC));
    }

    @Test(groups = "fast")
    public void testPerTenantDeltaIsApplied() {
        final long deltaMillis = 3L * 24 * 3600 * 1000; // +3 days
        final TenantAwareClock clock = clockWithDelta(String.valueOf(deltaMillis));

        // Without a tenant in context, the delta is not applied
        Assert.assertEquals(clock.getUTCNow(), BASE);

        // With the tenant in context, the delta is applied consistently across all accessors
        TenantClockContextHolder.setTenantRecordId(TENANT_RECORD_ID);
        final DateTime expected = BASE.plus(deltaMillis);
        Assert.assertEquals(clock.getUTCNow(), expected);
        Assert.assertEquals(clock.getNow(DateTimeZone.UTC), expected);
        Assert.assertEquals(clock.getUTCToday(), new LocalDate(expected, DateTimeZone.UTC));
        Assert.assertEquals(clock.getToday(DateTimeZone.UTC), new LocalDate(expected, DateTimeZone.UTC));
    }

    @Test(groups = "fast")
    public void testNegativeDeltaMovesClockBackwards() {
        final long deltaMillis = -2L * 3600 * 1000; // -2 hours
        final TenantAwareClock clock = clockWithDelta(String.valueOf(deltaMillis));
        TenantClockContextHolder.setTenantRecordId(TENANT_RECORD_ID);

        Assert.assertEquals(clock.getUTCNow(), BASE.minusHours(2));
    }

    @Test(groups = "fast")
    public void testMalformedDeltaFallsBackToDelegate() {
        final TenantAwareClock clock = clockWithDelta("not-a-number");
        TenantClockContextHolder.setTenantRecordId(TENANT_RECORD_ID);

        Assert.assertEquals(clock.getUTCNow(), BASE);
    }

    @Test(groups = "fast")
    public void testNullCacheConfigBehavesLikeDelegate() {
        final TenantAwareClock clock = new TenantAwareClock(fixedClock(BASE), null);
        TenantClockContextHolder.setTenantRecordId(TENANT_RECORD_ID);

        Assert.assertEquals(clock.getUTCNow(), BASE);
    }

    // Builds a TenantAwareClock whose per-tenant config exposes the given raw delta value (null => key absent).
    private TenantAwareClock clockWithDelta(final String rawDelta) {
        final PerTenantConfig perTenantConfig = new PerTenantConfig();
        if (rawDelta != null) {
            perTenantConfig.put(TenantAwareClock.PER_TENANT_CLOCK_DELTA_KEY, rawDelta);
        }
        return new TenantAwareClock(fixedClock(BASE), null) {
            @Override
            protected PerTenantConfig getPerTenantConfig(final Long tenantRecordId) {
                Assert.assertEquals(tenantRecordId.longValue(), TENANT_RECORD_ID);
                return perTenantConfig;
            }
        };
    }

    private static Clock fixedClock(final DateTime now) {
        return new Clock() {
            @Override
            public DateTime getNow(final DateTimeZone tz) {
                return now.toDateTime(tz);
            }

            @Override
            public DateTime getUTCNow() {
                return now;
            }

            @Override
            public LocalDate getUTCToday() {
                return new LocalDate(now, DateTimeZone.UTC);
            }

            @Override
            public LocalDate getToday(final DateTimeZone tz) {
                return new LocalDate(now, tz);
            }
        };
    }
}
