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

/**
 * Thread-local holder for the tenant currently being served on this thread.
 * <p/>
 * Kill Bill uses a single, injected {@link org.killbill.clock.Clock} instance everywhere. To support a
 * per-tenant (movable) clock without rewiring the hundreds of clock injection points, {@link TenantAwareClock}
 * consults this holder to figure out which tenant's clock delta should be applied.
 * <p/>
 * The holder is populated centrally in {@code InternalCallContextFactory} whenever an internal context is created.
 * Since every unit of work (REST request, bus event, notification queue callback, background janitor task) creates
 * its internal context before reading the clock, the "current tenant" is refreshed to the correct tenant ahead of
 * any clock access on that thread.
 */
public final class TenantClockContextHolder {

    private static final ThreadLocal<Long> CURRENT_TENANT_RECORD_ID = new ThreadLocal<Long>();

    private TenantClockContextHolder() {}

    public static void setTenantRecordId(@Nullable final Long tenantRecordId) {
        if (tenantRecordId == null) {
            CURRENT_TENANT_RECORD_ID.remove();
        } else {
            CURRENT_TENANT_RECORD_ID.set(tenantRecordId);
        }
    }

    @Nullable
    public static Long getTenantRecordId() {
        return CURRENT_TENANT_RECORD_ID.get();
    }

    public static void clear() {
        CURRENT_TENANT_RECORD_ID.remove();
    }
}
