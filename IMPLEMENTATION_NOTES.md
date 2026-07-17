# Issue #294 — Support a per-tenant clock

## Goal

Kill Bill injects a single, global `org.killbill.clock.Clock` in ~114 places. For multi-tenant
(test) deployments we want each tenant to be able to move its own clock without affecting other
tenants, while keeping behavior **identical to today** when no per-tenant clock is configured.

## Approach

The maintainer sketch was: store a per-tenant delta as key/value, populate the per-tenant clock in
the tenant context on each request, make KB read the clock from that context (removing clock
injection), and fix the bus/notification queues and background jobs to be tenant-aware.

Literally removing clock injection from ~114 sites (across this repo **and** the external
`killbill-api` / `killbill-commons` artifacts) is a multi-repo refactor far outside a focused,
reviewable change. Instead this implementation achieves the same observable behavior with a small,
central change: the **injected clock itself becomes tenant-aware**. Every existing
`clock.getUTCNow()` call automatically returns the current tenant's time, so no consumer code has to
change.

Three pieces:

1. **`TenantAwareClock`** (`util/.../clock/TenantAwareClock.java`) — a `Clock` decorator that wraps
   the real base clock (`DefaultClock` in production, `ClockMock` on test deployments) and adds a
   per-tenant delta (in milliseconds, relative to the base clock). With no tenant in context or no
   delta configured, it returns the base clock time unchanged.

2. **`TenantClockContextHolder`** (`util/.../clock/TenantClockContextHolder.java`) — a thread-local
   holding the `tenantRecordId` currently being served. `TenantAwareClock` reads it to decide which
   tenant's delta to apply.

3. **Central population** — the holder is set in `InternalCallContextFactory.populateMDCContext(...)`,
   which is the single choke point already called (right next to the MDC logging context) by *every*
   path that builds an internal context: REST requests, the persistent bus (`BeatrixListener`),
   notification-queue callbacks (invoice next-billing-date, overdue notifiers) and background
   janitors (`IncompletePaymentAttemptTask`, `IncompletePaymentTransactionTask`). Because each unit
   of work builds its internal context before touching the clock, the "current tenant" is refreshed
   to the right tenant ahead of any clock read on that thread. This is what makes the bus,
   notifications and background jobs tenant-aware for free.

### Where the delta is stored (existing per-tenant key/value mechanism)

The delta is **not** a new storage mechanism. It is a single entry inside the existing
`PER_TENANT_CONFIG` tenant config map (`TenantKV` key/value), under the reserved key
`org.killbill.tenant.clock.deltaMillis`. That map is already:

- persisted per tenant in the `tenant_kvs` table,
- cached in the `TENANT_CONFIG` cache and read through `CacheConfig.getPerTenantConfig(...)`,
- invalidated across nodes by the existing `PerTenantConfigInvalidationCallback`.

So `TenantAwareClock` gets caching and cross-node invalidation with zero new infrastructure, and an
unknown extra key is ignored by the config-magic config readers.

### Setting the per-tenant clock

- **Programmatically / via config upload**: add `"org.killbill.tenant.clock.deltaMillis":"<millis>"`
  to a tenant's per-tenant config (the same channel used for other per-tenant config).
- **Convenience test endpoint**: `POST /test/clock/tenant?requestedDate=...` (only present in test
  mode, mirroring the existing global `POST /test/clock`). It computes the delta between the
  requested time and the shared base clock, merges it into the tenant's `PER_TENANT_CONFIG` (without
  clobbering other config keys) and returns the resulting per-tenant time. Passing no date resets the
  tenant back to the base clock.

## Files changed

| File | Change |
| --- | --- |
| `util/.../clock/TenantClockContextHolder.java` | **New.** Thread-local for the current `tenantRecordId`. |
| `util/.../clock/TenantAwareClock.java` | **New.** `Clock` decorator applying the per-tenant delta; reads the delta from `CacheConfig`/`PerTenantConfig`. |
| `util/.../glue/ClockModule.java` | Bind the base clock under `@Named("delegateClock")` and bind `Clock` to `TenantAwareClock`. |
| `util/.../callcontext/InternalCallContextFactory.java` | Populate `TenantClockContextHolder` inside `populateMDCContext(...)`. |
| `profiles/.../modules/KillbillServerModule.java` | In test mode, wrap the movable `ClockMock` with `TenantAwareClock`. |
| `profiles/.../security/TenantFilter.java` | Clear the thread-local at the end of each request (avoid leaking tenant identity across pooled threads). |
| `jaxrs/.../resources/TestResource.java` | Unwrap `TenantAwareClock` for the existing global clock endpoints; add `POST /test/clock/tenant`. |
| `util/src/test/.../clock/TestTenantAwareClock.java` | **New.** Unit tests for the decorator (no tenant, no delta, positive/negative delta, malformed delta, null cache). |

## Testing

`TestTenantAwareClock` (6 fast tests) covers: no tenant in context → base time; tenant present but no
delta → base time; positive delta applied across `getUTCNow`/`getNow`/`getUTCToday`/`getToday`;
negative delta; malformed delta falls back to base time; null `CacheConfig` → base time.

- `mvn -q -DskipTests compile` — passes.
- `mvn -q -pl util test -Dtest=TestTenantAwareClock` — 6/6 pass.

## Known limitations / notes

- **Injection is not removed.** Consumers still receive an injected `Clock`; that clock is now
  tenant-aware. This is intentional to keep the change focused and reviewable rather than rewriting
  ~114 call sites across three repositories. The end behavior (per-tenant time) is equivalent.
- **Ambient tenant via thread-local.** Because ~114 call sites read the injected clock (not the
  `TenantContext`), the current tenant has to be discovered ambiently. It is refreshed at the start
  of every unit of work (context creation) and cleared per HTTP request. Any code that reads the
  clock *without first creating an internal context* on that thread would see the previously-served
  tenant's delta; in practice every request/bus/notification/janitor path creates its context first.
- **Queue readiness vs. business time.** The notification queue / persistent bus decide which events
  are *ready to fire* using the base clock on the polling thread (no single tenant is in context
  while scanning across tenants). Once an event is dispatched, its handler builds a tenant-scoped
  internal context, so all business logic inside the handler runs on that tenant's clock. Scheduling
  events on wall-clock time while running handler logic on tenant time is the intended behavior; a
  fully per-tenant *scheduling* layer would require changes in the external `killbill-queue` library.
- **Delta unit.** The delta is stored as signed milliseconds relative to the base clock. On test
  deployments the base clock is the shared movable `ClockMock`, so a tenant's effective time is
  `ClockMock time + tenant delta`.
- **Cross-node propagation** of a delta change is bounded by the existing `PER_TENANT_CONFIG` cache
  invalidation (same as any other per-tenant config change) — not instantaneous across a cluster.
