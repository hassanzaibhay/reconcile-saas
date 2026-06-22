# Architecture & invariants

## Non-negotiable invariants

1. **Tenant isolation is sacred.** Every DB connection resolves to the caller's tenant
   schema before any query. No query ever runs against the wrong schema. There is no
   "admin bypass" that reads across tenants without an explicit, audited cross-tenant use
   case. If tenant context is missing on a data-access path, fail closed (throw), never
   default to a tenant.
2. **No secrets in the repo.** No credentials, keys, or connection strings in source or
   committed config. Use env vars + `.env` (gitignored) locally.
3. **Money is never a `double`.** Use `BigDecimal` / a `Money` value type with explicit
   currency and rounding. Reconciliation correctness depends on this.
4. **Migrations are forward-only and reviewed.** Schema changes are Flyway migrations,
   never `ddl-auto` against anything but a throwaway test DB. Tenant migrations apply to
   every tenant schema atomically per release.
5. **Reconciliation is deterministic and auditable.** Same inputs + same rule set →
   same matches. Every match/unmatch decision records who/what/when/which-rule.
6. **The domain layer stays framework-free.** No `@Component`, no JPA annotations, no
   `Spring*` imports under any `domain` package.

## Build / test / run

```bash
./gradlew build                    # full build + all checks
./gradlew test                     # unit tests
./gradlew integrationTest          # Testcontainers-backed
./gradlew :modulith-verification:test   # Spring Modulith boundary verification
./gradlew spotlessApply            # format (run before commit)
./gradlew spotlessCheck            # CI gate
docker compose up -d               # full local stack (app + postgres + redis + otel)
docker compose up -d postgres redis otel-collector   # infra only, app via bootRun
./gradlew bootRun                  # run app on host (needs infra up)
./gradlew bootBuildImage           # OCI image via Paketo buildpacks (no Dockerfile)
docker build -t reconcile-saas:local .   # alt: hand-written multi-stage image
```

The app ships as a layered Spring Boot image. A multi-stage `Dockerfile` (layered-jar
extraction, non-root user, JRE-slim base) is the source of truth; `bootBuildImage` is
the buildpack alternative. The image runs with the `container` Spring profile.

Definition of done for any change: `spotlessCheck`, `build`, `test`, `integrationTest`,
and modulith verification all green.

## Guardrails

- Don't edit an already-applied migration under `db/migration/*` — write a new one instead.
- Don't add cross-tenant data access without an explicit approved use case.
- Don't introduce a new third-party dependency without flagging it and the reason.
- Don't weaken a security control (auth, validation, tenant scoping) to make a test pass.
- Don't touch another module's `internal` package.

See `docs/rules/multitenancy.md` and `docs/rules/reconciliation-domain.md` for detailed
domain conventions.
