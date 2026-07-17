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

import javax.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.config.tenant.CacheConfig;
import org.killbill.billing.util.config.tenant.PerTenantConfig;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Clock} that decorates a base clock (typically {@link org.killbill.clock.DefaultClock} in production or
 * {@code ClockMock} on test deployments) and applies a per-tenant time delta.
 * <p/>
 * The delta (in milliseconds, relative to the base clock time) is stored using the existing per-tenant key/value
 * mechanism: it lives in the {@code PER_TENANT_CONFIG} tenant config map under {@link #PER_TENANT_CLOCK_DELTA_KEY}
 * and is read through the already-cached (and cross-node invalidated) {@link CacheConfig}.
 * <p/>
 * The tenant to apply is resolved from {@link TenantClockContextHolder} (populated per request / per background task).
 * When no tenant is in context, or the tenant has no delta configured, this clock behaves exactly like the base clock,
 * preserving today's behavior.
 */
public class TenantAwareClock implements Clock {

    private static final Logger logger = LoggerFactory.getLogger(TenantAwareClock.class);

    public static final String DELEGATE_CLOCK_NAMED = "delegateClock";

    // Key inside the PER_TENANT_CONFIG map holding the per-tenant clock delta, expressed in milliseconds
    // relative to the base clock (positive means the tenant clock is ahead of the base clock).
    public static final String PER_TENANT_CLOCK_DELTA_KEY = "org.killbill.tenant.clock.deltaMillis";

    // Guard against re-entrancy: resolving the delta may (on a cache miss) trigger work that itself reads the clock.
    private static final ThreadLocal<Boolean> RESOLVING_DELTA = new ThreadLocal<Boolean>();

    private final Clock delegate;
    private final CacheConfig cacheConfig;

    @Inject
    public TenantAwareClock(@Named(DELEGATE_CLOCK_NAMED) final Clock delegate,
                            @Nullable final CacheConfig cacheConfig) {
        this.delegate = delegate;
        this.cacheConfig = cacheConfig;
    }

    @Override
    public DateTime getNow(final DateTimeZone tz) {
        return applyDelta(delegate.getNow(tz));
    }

    @Override
    public DateTime getUTCNow() {
        return applyDelta(delegate.getUTCNow());
    }

    @Override
    public LocalDate getUTCToday() {
        final long delta = currentDeltaMillis();
        return delta == 0L ? delegate.getUTCToday() : new LocalDate(delegate.getUTCNow().plus(delta), DateTimeZone.UTC);
    }

    @Override
    public LocalDate getToday(final DateTimeZone tz) {
        final long delta = currentDeltaMillis();
        return delta == 0L ? delegate.getToday(tz) : new LocalDate(delegate.getNow(tz).plus(delta), tz);
    }

    /**
     * @return the wrapped base clock (e.g. the {@code ClockMock} on test deployments).
     */
    public Clock getDelegate() {
        return delegate;
    }

    private DateTime applyDelta(final DateTime dateTime) {
        final long delta = currentDeltaMillis();
        return delta == 0L ? dateTime : dateTime.plus(delta);
    }

    private long currentDeltaMillis() {
        final Long tenantRecordId = TenantClockContextHolder.getTenantRecordId();
        if (tenantRecordId == null) {
            return 0L;
        }

        // Avoid infinite recursion if resolving the delta ends up reading the clock again on this thread.
        if (Boolean.TRUE.equals(RESOLVING_DELTA.get())) {
            return 0L;
        }

        RESOLVING_DELTA.set(Boolean.TRUE);
        try {
            final PerTenantConfig perTenantConfig = getPerTenantConfig(tenantRecordId);
            if (perTenantConfig == null) {
                return 0L;
            }
            final String rawDelta = perTenantConfig.get(PER_TENANT_CLOCK_DELTA_KEY);
            if (rawDelta == null || rawDelta.isEmpty()) {
                return 0L;
            }
            return Long.parseLong(rawDelta.trim());
        } catch (final NumberFormatException e) {
            logger.warn("Ignoring malformed per-tenant clock delta for tenantRecordId={}", tenantRecordId, e);
            return 0L;
        } catch (final RuntimeException e) {
            // Never let clock resolution take down a request/background task; fall back to the base clock.
            logger.warn("Unable to resolve per-tenant clock delta for tenantRecordId={}, using base clock", tenantRecordId, e);
            return 0L;
        } finally {
            RESOLVING_DELTA.remove();
        }
    }

    // Testing seam: resolves the per-tenant config holding the clock delta. Isolated so it can be overridden in tests.
    protected PerTenantConfig getPerTenantConfig(final Long tenantRecordId) {
        return cacheConfig == null ? null : cacheConfig.getPerTenantConfig(new InternalTenantContext(tenantRecordId));
    }
}
