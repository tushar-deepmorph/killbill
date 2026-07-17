# Kill Bill issue #294 implementation notes

## Approach

This change introduces `TenantClock`, a small utility service that computes tenant-adjusted time from the existing global `Clock` plus a per-tenant delta stored in the existing `PER_TENANT_CONFIG` tenant key/value JSON.

Supported per-tenant config properties:

- `org.killbill.clock.tenant.delta.millis`
- `org.killbill.clock.tenant.delta.seconds`

If neither property is configured, the delta is zero and behavior is unchanged. This preserves the existing system clock and `ClockMock` behavior.

`InternalCallContextFactory` now creates internal call contexts with tenant-adjusted `createdDate` and `updatedDate` whenever it has resolved the tenant record id. This is the main request-path integration point: public `CallContext` remains API-compatible, while internal Kill Bill code receives per-tenant timestamps.

Notification enqueue paths convert tenant-effective dates back to global queue timestamps before storing them. This lets the existing notification queue, which still polls using the global clock internally, fire notifications when the tenant-adjusted clock reaches the intended effective date.

## Files changed

- Added `util/src/main/java/org/killbill/billing/util/clock/TenantClock.java`.
- Updated `InternalCallContextFactory` to use tenant-adjusted context timestamps.
- Updated invoice date computations and invoice notification posters.
- Updated payment retry and janitor scheduling; the janitor scheduled scan now fetches broad candidates and applies per-tenant age filtering in Java.
- Updated overdue notification posting.
- Updated selected entitlement paths that already had a tenant context available.

## Verification

`mvn -q -DskipTests compile` passes from the repository root.

## Known limitations

This is a focused implementation, not a full removal of global `Clock` injection everywhere. Some code paths still inject `Clock` directly when they do not have tenant context or when changing them would require wider API/constructor plumbing.

The notification queue implementation itself remains global-clock based. This change compensates at enqueue boundaries by storing global-equivalent timestamps for tenant-effective times.

Some entitlement notification scheduling performed inside `DefaultEntitlement` still uses the local object constructor path and was left for a follow-up to avoid a large constructor-threading change across the entitlement API base.
