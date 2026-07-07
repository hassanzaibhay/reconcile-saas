# Reconciliation Domain Rules

## Contracts

- `MatchingEngine` — pure interface in `reconciliation.domain`. No Spring. No JPA.
- `MatchRule` — sealed interface; current permits: `ExactAmountAndDateRule`,
  `AmountToleranceDateDriftRule`.
- `MatchingAxis` — enum: `SUM_TO_ZERO` (pair sums to zero, opposite-sign debit/credit, default)
  or `DIFFERENCE` (pair is approximately equal, same-sign bank/ledger).
- `Discrepancy` — record with `Reason` enum; current values: `NO_COUNTERPART`.
- `AmbiguousCluster` — immutable record; sorted member set only, no distinguished source.
- `MatchRunResult` — immutable record: `matches`, `ambiguousClusters`, `discrepancies`.

## Uniform graph-partition algorithm

Both exact and tolerance passes use the **same** graph-partition logic. There is no
`MatchPassPolicy`, no `MatchOutcome` sealed type, no greedy pairing. Every rule exposes:

```java
List<LedgerEntry> neighbors(LedgerEntry candidate, List<LedgerEntry> pool);
```

For each pass the engine:

1. **Freezes** the current working pool (snapshot).
2. Calls `rule.neighbors()` for every node against the frozen snapshot → undirected
   adjacency map.
3. Finds **connected components** via BFS; start nodes in UUID sort order.
4. Partitions by component size:

| Size | Outcome               | Pool effect            |
|------|-----------------------|------------------------|
| 1    | Isolated; next pass   | Stays in working pool  |
| 2    | Matched pair          | Both removed           |
| ≥ 3  | AmbiguousCluster      | All removed            |

**Exact precedence** comes from pass order only: the exact rule runs first and drains its
entries before the tolerance rule sees the residual. Size-1 isolated nodes flow through
to the next pass unchanged.

**Why frozen snapshot**: without it, greedy removal during a pass creates dangling
candidates — an entry R may cite Q as its only neighbour, then Q is consumed by P,
leaving R with no valid neighbour and an incorrect result. The frozen snapshot guarantees
the adjacency graph is a function of the pool state at the start of the pass, not of
traversal order.

## Adjacency contract (`neighbors()`)

- Must not include `candidate` itself.
- Must only return entries currently in `pool` (the caller passes the frozen snapshot).
- Must be **symmetric**: if `b ∈ neighbors(a, pool)` then `a ∈ neighbors(b, pool)`.
- Must be **deterministic**: same inputs → same list (or set — engine re-sorts by UUID).

## `ExactAmountAndDateRule` behavior

Two entries are adjacent when **all** hold:
- Different `feedId`.
- Same `currency`.
- Same `entryDate`.
- `axis.isExactMatch(a.amount(), b.amount())` — axis-aware exact check via
  `MatchingAxis.amountDiff() == 0`. Default `SUM_TO_ZERO`: `a + b = 0` (opposite signs,
  equal magnitudes). `DIFFERENCE`: `a − b = 0` (same sign, equal values).

Returns **all** qualifying neighbors (not just first), enabling 3-way contention
detection. 3+ node components become `AmbiguousCluster`.

Constructor: `ExactAmountAndDateRule()` defaults to `SUM_TO_ZERO`. Pass `MatchingAxis`
explicitly for same-sign feeds.

## `AmountToleranceDateDriftRule` behavior

Two entries are adjacent when **all** hold:
- Different `feedId`.
- Same `currency`.
- Date drift ≤ `ToleranceConfig.maxDateDriftDays` (absolute days, either direction).
- Amount within tolerance (**OR** semantics — either clause suffices):
  - `diff ≤ absoluteTolerance`  **OR**
  - `diff / max(|a|, |b|) ≤ percentageTolerance` (HALF_EVEN, scale 10)
  - Zero-base skip: if `max(|a|, |b|) = 0`, percentage clause is skipped; absolute governs.

**Numerator `diff = config.axis().amountDiff(a, b)`** — axis-aware, same helper as the exact
rule:
- `SUM_TO_ZERO` → `|a + b|` (distance from summing to zero; for opposite-sign legs equals
  `||a| − |b||`; large for same-sign → rejects two-debit / two-credit false matches)
- `DIFFERENCE` → `|a − b|` (distance from being equal; for same-sign legs equals
  `||a| − |b||`; large for opposite-sign → rejects cross-convention false matches)

**Percentage denominator** is `max(|a|, |b|)` — the larger magnitude. Sign-independent;
makes the formula symmetric (same verdict regardless of which entry is `candidate`).

## `MatchingAxis`

Per-tenant convention for what "equal amount" means:

| Axis | Exact condition | Tolerance numerator | Feed convention |
|------|----------------|---------------------|-----------------|
| `SUM_TO_ZERO` (default) | `a + b = 0` | `\|a + b\|` | Opposite-sign debit/credit |
| `DIFFERENCE` | `a − b = 0` | `\|a − b\|` | Same-sign bank/ledger |

Stored in `ToleranceConfig.axis`. Both rules call `axis.amountDiff()` / `axis.isExactMatch()`
so they can never diverge. Invariant: any exact pair under an axis is within tolerance of
itself under the same axis (`exactMatchImpliesWithinTolerance` test).

`ToleranceConfig` 4-arg constructor defaults `axis` to `SUM_TO_ZERO`.

**SLICE-1 MIGRATION**: add `axis VARCHAR(20) NOT NULL DEFAULT 'SUM_TO_ZERO'` to the V6
migration and carry it through the persistence layer when sub-slice 1 lands.

## `ToleranceConfig`

Pure domain record (no Spring, no JPA). Fields: `id`, `absoluteTolerance`,
`percentageTolerance`, `maxDateDriftDays`. `ToleranceConfig.defaults()` returns
abs=0.01, pct=0.001, drift=0.

## `AmbiguousCluster` semantics

- Constructor sorts members ascending by `LedgerEntryId.value()` (UUID string order)
  and copies the list immutably.
- No entry is distinguished as "source" — the cluster is a flat set of contending IDs.
- Every member records an `AuditDecision` with decision=`AMBIGUOUS`.

## Determinism requirement

Same inputs + same rule list (insertion-ordered) → **identical** matches map, cluster
member sets, and discrepancy list. Guaranteed by:

1. Initial sort of all entries by `id` before any pass.
2. Frozen-snapshot adjacency (no mid-pass removal).
3. BFS start nodes in UUID sort order.
4. Components sorted by min-member UUID before processing.
5. `AmbiguousCluster.members()` sorted on construction.

## Auditability requirement

Every accept/reject decision persists an `AuditDecision`:
```
(runId, ruleId, entryId, decidedAt, decidedBy=SYSTEM, decision, reason)
```
Stored in `audit_decision` table. **Never deletable.** The `MatchRunCompletedEvent`
carries the run summary for downstream consumers (notification, reporting).

## Money rules

`Money` lives in `shared.domain` (not `reconciliation.domain`) — every module that
touches a monetary value depends on `:shared:domain`.

- `BigDecimal` + `Currency` — never `double`.
- Constructor scales with `HALF_EVEN` to `currency.getDefaultFractionDigits()`.
- Arithmetic methods guard against currency mismatch (throw `IllegalArgumentException`).
- `HALF_EVEN` only — no other rounding mode anywhere in the codebase.

## Adding a new matching rule safely

1. Implement `MatchRule` (sealed — add to `permits` list in `MatchRule.java`).
2. Write a deterministic unit test: same fixture → same audit trail across two runs.
3. Write a Testcontainers integration test: assert the rule persists `AuditDecision`
   rows with the correct `ruleId` and `decision`.
4. Update `reconciliation-domain.md` permits list and behavior section.
