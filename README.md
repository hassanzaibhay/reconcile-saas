# reconcile-saas

Multi-tenant financial reconciliation SaaS. Java 21 / Spring Boot 3.5.x / Spring Modulith / PostgreSQL 16 schema-per-tenant.

## Quick start

### Option A — full stack via Docker Compose

```bash
cp .env.example .env       # fill in passwords
docker compose up -d       # starts app + postgres + redis + otel-collector
curl http://localhost:8080/actuator/health
```

### Option B — local dev (infra via Compose, app via Gradle)

```bash
cp .env.example .env
docker compose up -d postgres redis otel-collector   # infra only (app disabled in override)
./gradlew bootRun                                     # run app on host JVM
```

## Build & test

```bash
./gradlew build                          # compile + spotlessCheck + unit tests
./gradlew test                           # unit tests (no Docker needed)
./gradlew :modulith-verification:test    # Spring Modulith boundary + ArchUnit checks
./gradlew :app:integrationTest           # Testcontainers integration tests (Docker required)
./gradlew spotlessApply                  # auto-format before commit
```

## Module overview

| Module | Responsibility |
|---|---|
| `shared` | TenantId, TenantContext, Money, TenantScopedEvent |
| `tenant` | Tenant provisioning, Flyway orchestration |
| `iam` | JWT validation, TenantFilter, AsyncConfig |
| `ingestion` | CSV upload, Spring Batch, idempotency via Redis |
| `ledger` | Canonical ledger entry store |
| `reconciliation` | Matching engine, ExactAmountAndDateRule, audit decisions |
| `reporting` | Run summary + discrepancy endpoint |
| `notification` | TenantScopedEvent listeners, audit_log storage |
| `app` | Boot entrypoint, multi-tenancy persistence wiring |
| `modulith-verification` | Module boundary + ArchUnit domain-purity checks |

## Key invariants

- **Tenant isolation**: every DB connection sets `search_path` to the caller's tenant schema.  
  Missing context → `MissingTenantException` (fail-closed, never defaults).
- **No `double` for money**: `Money(BigDecimal, Currency)` with `HALF_EVEN` rounding.
- **Migrations forward-only**: Flyway, never `ddl-auto`. Per-tenant schema created on provision.
- **Deterministic reconciliation**: same inputs + same rule set → same matches + audit trail.

See `docs/ARCHITECTURE.md` for the full invariant list and guardrails, and
`docs/rules/multitenancy.md` / `docs/rules/reconciliation-domain.md` for detailed conventions.
