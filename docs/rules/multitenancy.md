# Multi-Tenancy Rules

## Architecture

Schema-per-tenant, single Hikari pool. `SchemaPerTenantConnectionProvider` issues a
session-level `SET search_path TO <schema>` on connection acquisition and `RESET search_path`
on release.

**Never use `SET LOCAL`** — it reverts at transaction end and is a no-op under autocommit
reads, silently routing subsequent queries to the default schema (the shared registry).

**Never include `public` in the tenant search path.** Tenant tables live only in
`tenant_<hex-uuid>`. Extensions go in `shared_ext` if needed.

## Bootstrap pins (do not remove without reading the why)

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none                                 # Flyway owns schema
    properties:
      hibernate:
        multiTenancy: SCHEMA
        boot:
          allow_jdbc_metadata_access: false          # prevents EMF probing a connection at startup
  flyway:
    enabled: false                                   # orchestrated manually
```

`allow_jdbc_metadata_access: false` — without it, Hibernate opens a metadata connection during
`EntityManagerFactory` creation. At that point `TenantContext` is unset; the resolver throws
`MissingTenantException`, or worse, silently hits the default schema if the guard is ever weakened.

## TenantContext lifecycle

Set by `TenantFilter` from the `tid` JWT claim. Cleared in `finally`. `current()` throws
`MissingTenantException` when unset — **fail-closed, never defaults.**

## Async propagation (all three paths are mandatory)

1. **`@Async` / executors** — `TenantAwareTaskDecorator` on every `ThreadPoolTaskExecutor`.
   Captures `TenantContext` at submit-time; sets/clears on worker thread.
2. **Spring Batch** — `TenantJobExecutionListener` reads `tenantId` from a mandatory
   `JobParameter`. A `StepExecutionListener` applies the same for partitioned steps.
3. **`@ApplicationModuleListener`** — `TenantAwareEventListenerAdvice` (`BeanPostProcessor`)
   wraps every listener. Reads `TenantScopedEvent#tenantId()`, sets context, clears in finally.
   ArchUnit enforces that every `@ApplicationModuleListener` method has exactly one parameter
   that is a `TenantScopedEvent` subtype.

## How to add a tenant

`TenantProvisioningService.provision(slug)` → registry row + Flyway migrate new schema +
publish `TenantProvisionedEvent`. **Same Flyway code path as boot** — no drift.

## How to add a per-tenant migration

Drop `V<n>__<name>.sql` in `db/migration/tenant/`. Runs on next boot for every tenant
and via `TenantProvisioningService` for new tenants.

## Flyway ordering guarantee

`SharedFlywayMigrator` exposes a `@Bean Flyway sharedFlyway(DataSource)` that calls `.migrate()`
inside the factory method. `PersistenceConfig` creates `HibernatePropertiesCustomizer`
`@DependsOn("sharedFlyway")`, ensuring the public schema exists before the `EntityManagerFactory`
is wired.

## Migration scope ceiling

Boot-time migrate-all-tenants assumes a **single application replica during boot**. Above ~50
tenants or any multi-replica deploy, decouple to a dedicated migration Job (k8s) gated on a
Redis lease (`SET tenant-migration-lease NX PX 600000`). App pods start `READY=false` until
the lease is released, signaled via `public.tenant_migration_state`.

## Registry access (public.tenants) must bypass the tenant resolver

All reads/writes to `public.tenants` (and any other registry-level table) **must use `JdbcTemplate`
with an explicit schema-qualified table name** (`public.tenants`, never just `tenants`).
They must never go through JPA repositories or any code path that invokes the tenant resolver.

**Why:** The resolver calls `TenantContext.current()` and throws `MissingTenantException` when
the context is unset. Registry reads happen during boot (migration runner, tenant lookup before
a tenant is in context) and during provisioning (before the new schema exists). Routing them
through the resolver causes a circular dependency or a fail-closed throw at the exact moment
you need to read or write the registry. `JdbcTemplate` bypasses Hibernate's tenant machinery
entirely and keeps the public schema queries predictable and independent of context state.

## No `@Transactional` on `TenantProvisioningService.provision()`

`provision()` must **not** be annotated `@Transactional` or called from within a JPA transaction.

**Why:** Spring's `@Transactional` AOP proxy opens an `EntityManager` at method entry. Opening
an `EntityManager` causes Hibernate to call `resolveCurrentTenantIdentifier()`. At that point
no tenant schema exists yet for the tenant being provisioned — `TenantContext` is unset for the
new tenant and the resolver throws `MissingTenantException`, aborting the provisioning before the
Flyway migration can run. Registry persistence (`tenantRepository.save()`) is done via `JdbcTemplate`
directly (see rule above), so transactional semantics are not needed on the outer method; the
Flyway migration step is its own DDL transaction internally.

## Approved cross-tenant operations

*(empty — initial list)* Adding one requires a new `sealed permits` entry on
`CrossTenantOperation` and an audit-row template.

## Per-tenant test fixture

`TenantTestExtension` + `@TenantTest` provision a UUID-named schema in a Testcontainers
Postgres, set `TenantContext`, and drop on teardown.
