/*
 * Copyright 2026 The Billing Project, LLC
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

import javax.annotation.Nullable;
import jakarta.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.config.tenant.CacheConfig;
import org.killbill.billing.util.config.tenant.PerTenantConfig;
import org.killbill.clock.Clock;

public class TenantClock {

    public static final String TENANT_CLOCK_DELTA_MILLIS = "org.killbill.clock.tenant.delta.millis";
    public static final String TENANT_CLOCK_DELTA_SECONDS = "org.killbill.clock.tenant.delta.seconds";

    private final Clock clock;
    private final CacheConfig cacheConfig;

    @Inject
    public TenantClock(final Clock clock, final CacheConfig cacheConfig) {
        this.clock = clock;
        this.cacheConfig = cacheConfig;
    }

    public TenantClock(final Clock clock) {
        this.clock = clock;
        this.cacheConfig = null;
    }

    public DateTime getUTCNow(final InternalTenantContext tenantContext) {
        return applyDelta(clock.getUTCNow(), tenantContext);
    }

    public DateTime getNow(final DateTimeZone timeZone, final InternalTenantContext tenantContext) {
        return applyDelta(clock.getNow(timeZone), tenantContext);
    }

    public LocalDate getUTCToday(final InternalTenantContext tenantContext) {
        return getUTCNow(tenantContext).toLocalDate();
    }

    public LocalDate getToday(final DateTimeZone timeZone, final InternalTenantContext tenantContext) {
        return getNow(timeZone, tenantContext).toLocalDate();
    }

    public DateTime applyDelta(final DateTime input, final InternalTenantContext tenantContext) {
        final long deltaMillis = getDeltaMillis(tenantContext);
        return deltaMillis == 0L ? input : input.plus(deltaMillis);
    }

    public DateTime toGlobalDateTime(final DateTime tenantDateTime, final InternalTenantContext tenantContext) {
        final long deltaMillis = getDeltaMillis(tenantContext);
        return deltaMillis == 0L ? tenantDateTime : tenantDateTime.minus(deltaMillis);
    }

    public long getDeltaMillis(@Nullable final InternalTenantContext tenantContext) {
        if (tenantContext == null || tenantContext.getTenantRecordId() == null || cacheConfig == null) {
            return 0L;
        }

        final PerTenantConfig perTenantConfig = cacheConfig.getPerTenantConfig(tenantContext);
        final String deltaMillis = perTenantConfig.get(TENANT_CLOCK_DELTA_MILLIS);
        if (deltaMillis != null) {
            return parseLong(deltaMillis, TENANT_CLOCK_DELTA_MILLIS);
        }

        final String deltaSeconds = perTenantConfig.get(TENANT_CLOCK_DELTA_SECONDS);
        if (deltaSeconds != null) {
            return parseLong(deltaSeconds, TENANT_CLOCK_DELTA_SECONDS) * 1000L;
        }

        return 0L;
    }

    private long parseLong(final String input, final String propertyName) {
        try {
            return Long.parseLong(input.trim());
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(String.format("Invalid per-tenant clock delta for property '%s': '%s'", propertyName, input), e);
        }
    }
}
