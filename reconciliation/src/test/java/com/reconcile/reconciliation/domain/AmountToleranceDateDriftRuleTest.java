package com.reconcile.reconciliation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.reconciliation.application.DefaultMatchingEngine;
import com.reconcile.shared.domain.Money;
import com.reconcile.shared.domain.TenantContext;
import com.reconcile.shared.domain.TenantId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for AmountToleranceDateDriftRule predicate and the engine's graph-cluster behaviour
 * driven by that rule. No Spring context, no Testcontainers.
 */
class AmountToleranceDateDriftRuleTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final LocalDate JAN_1 = LocalDate.of(2025, 1, 1);
    private static final LocalDate JAN_2 = LocalDate.of(2025, 1, 2);
    private static final LocalDate JAN_4 = LocalDate.of(2025, 1, 4);

    // Default config: absolute=0.05, pct=0.001 (0.1%), drift=2 days
    private static final ToleranceConfig CFG =
            new ToleranceConfig(null, new BigDecimal("0.05"), new BigDecimal("0.001"), 2);

    private AmountToleranceDateDriftRule rule;
    private DefaultMatchingEngine engine;

    @BeforeEach
    void setUp() {
        rule = new AmountToleranceDateDriftRule(CFG);
        engine = new DefaultMatchingEngine(decision -> {}, event -> {});
        TenantContext.set(TenantId.of(UUID.randomUUID()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------------------
    // Tolerance predicate — neighbors()
    // -------------------------------------------------------------------------

    /** Relation is symmetric: adjacent(a,b) ↔ adjacent(b,a) for every sampled pair. */
    @Test
    void toleranceRelationIsSymmetric() {
        // (1) within absolute tolerance — should be symmetric
        LedgerEntry a1 = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b1 = usd("feed-b", "-100.03", JAN_1);
        assertSymmetric(a1, b1, true);

        // (2) different currency — should be symmetric (neither adjacent)
        LedgerEntry a2 = entry("feed-a", "100.00", USD, JAN_1);
        LedgerEntry b2 = entry("feed-b", "-100.00", EUR, JAN_1);
        assertSymmetric(a2, b2, false);

        // (3) same feed — should be symmetric (neither adjacent)
        LedgerEntry a3 = usd("same-feed", "100.00", JAN_1);
        LedgerEntry b3 = usd("same-feed", "-100.00", JAN_1);
        assertSymmetric(a3, b3, false);

        // (4) amount boundary — exactly at absolute limit, should be symmetric
        LedgerEntry a4 = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b4 = usd("feed-b", "-100.05", JAN_1); // diff = 0.05 == limit → adjacent
        assertSymmetric(a4, b4, true);

        // (5) amount just over absolute limit, within percentage
        // diff=0.04, pct=0.04/100.04≈0.0004 < 0.001 → adjacent via pct
        LedgerEntry a5 = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b5 = usd("feed-b", "-100.04", JAN_1);
        assertSymmetric(a5, b5, true);

        // (6) date at drift boundary (2 days) — adjacent
        LedgerEntry a6 = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b6 = usd("feed-b", "-100.00", JAN_4); // Jan 1 + 3 days > 2 → NOT adjacent
        // wait: |Jan_1 - Jan_4| = 3 days > 2 drift limit → NOT adjacent
        assertSymmetric(a6, b6, false);

        // (7) date exactly at drift boundary (2 days) — adjacent
        LedgerEntry a7 = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b7 = usd("feed-b", "-100.00", JAN_2); // 1 day ≤ 2 → adjacent
        assertSymmetric(a7, b7, true);
    }

    @Test
    void amountExceedsAbsoluteAndPercentageTolerance() {
        // diff = |100 − 110| = 10; pct = 10/110 ≈ 0.091 >> 0.001 → NOT adjacent
        LedgerEntry a = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b = usd("feed-b", "-110.00", JAN_1);
        assertThat(rule.neighbors(a, List.of(a, b))).doesNotContain(b);
        assertThat(rule.neighbors(b, List.of(a, b))).doesNotContain(a);
    }

    @Test
    void dateDriftExceedsLimit() {
        // amount within tolerance but date is 3 days apart (limit = 2)
        LedgerEntry a = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b = usd("feed-b", "-100.02", JAN_4); // Jan_1 + 3 days
        assertThat(rule.neighbors(a, List.of(a, b))).doesNotContain(b);
    }

    /** OR semantics: within absolute only (pct would fail) → adjacent. */
    @Test
    void amountWithinAbsoluteTolerance() {
        // diff = 0.05 == absoluteTolerance → passes absolute clause
        // pct = 0.05/100.05 ≈ 0.0005 < 0.001 → also passes pct (both pass, but only OR needed)
        // Use a tighter pct config to isolate absolute path:
        ToleranceConfig tightPct = new ToleranceConfig(null, new BigDecimal("0.05"), new BigDecimal("0.0001"), 0);
        AmountToleranceDateDriftRule r = new AmountToleranceDateDriftRule(tightPct);
        // diff = 0.05 ≤ 0.05 (abs) → adjacent regardless of pct
        LedgerEntry a = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b = usd("feed-b", "-100.05", JAN_1);
        assertThat(r.neighbors(a, List.of(a, b))).contains(b);
    }

    /** OR semantics: within percentage only (absolute would fail) → adjacent. */
    @Test
    void amountWithinPercentageTolerance() {
        // diff = 0.10; abs limit = 0.05 → absolute fails
        // pct = 0.10 / 100.10 ≈ 0.001 ≤ 0.001 → passes percentage clause
        ToleranceConfig absOnly = new ToleranceConfig(null, new BigDecimal("0.05"), new BigDecimal("0.001"), 0);
        AmountToleranceDateDriftRule r = new AmountToleranceDateDriftRule(absOnly);
        LedgerEntry a = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b = usd("feed-b", "-100.10", JAN_1); // diff=0.10 > 0.05 abs; pct=0.10/100.10≈0.000999≤0.001
        assertThat(r.neighbors(a, List.of(a, b))).contains(b);
    }

    /** When max(|a|,|b|)=0 the percentage clause is skipped; absolute governs. */
    @Test
    void zeroBaseAmountSkipsPercentage() {
        // Both amounts are zero — diff=0 ≤ absoluteTolerance → adjacent
        LedgerEntry a = usd("feed-a", "0.00", JAN_1);
        LedgerEntry b = usd("feed-b", "0.00", JAN_1);
        assertThat(rule.neighbors(a, List.of(a, b))).contains(b);
    }

    /** Cross-currency entries are never adjacent even if numerically within tolerance. */
    @Test
    void crossCurrencySkipped() {
        LedgerEntry a = entry("feed-a", "100.00", USD, JAN_1);
        LedgerEntry b = entry("feed-b", "-100.00", EUR, JAN_1);
        assertThat(rule.neighbors(a, List.of(a, b))).doesNotContain(b);
        assertThat(rule.neighbors(b, List.of(a, b))).doesNotContain(a);
    }

    /**
     * Percentage denominator is max(|a|,|b|): neighbors(a,[b]) and neighbors(b,[a]) produce the
     * same adjacency verdict, proving the formula is side-independent.
     */
    @Test
    void percentageDenominatorIsSymmetric() {
        // diff = 0.50; max(100.00, 100.50) = 100.50; pct = 0.50/100.50 ≈ 0.004975 ≤ 0.006
        ToleranceConfig cfg = new ToleranceConfig(null, new BigDecimal("99"), new BigDecimal("0.006"), 0);
        AmountToleranceDateDriftRule r = new AmountToleranceDateDriftRule(cfg);
        LedgerEntry a = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b = usd("feed-b", "-100.50", JAN_1);
        assertThat(r.neighbors(a, List.of(a, b))).contains(b);
        assertThat(r.neighbors(b, List.of(a, b))).contains(a);
    }

    // -------------------------------------------------------------------------
    // Cluster graph semantics (engine-level)
    // -------------------------------------------------------------------------

    /**
     * P–Q adjacent, Q–R adjacent, P–R NOT adjacent (path graph). Component {P,Q,R} must be ONE
     * AmbiguousCluster. R must NOT appear as a dangling candidate citing a simultaneously matched
     * Q.
     */
    @Test
    void danglingCandidateRegression() {
        // P–Q: |p+q| = 0.03 ≤ 0.05 → adjacent
        // Q–R: |q+r| = |-100.03+100.06| = 0.03 ≤ 0.05 → adjacent (R has OPPOSITE sign to Q)
        // P–R: |p+r| = |100+100.06| = 200.06 → NOT adjacent (same sign)
        LedgerEntry p = usd("feed-p", "100.00", JAN_1);
        LedgerEntry q = usd("feed-q", "-100.03", JAN_1);
        LedgerEntry r = usd("feed-r", "100.06", JAN_1);

        MatchRunResult result =
                engine.run(MatchRunId.generate(), List.of(new AmountToleranceDateDriftRule(CFG)), List.of(p, q, r));

        assertThat(result.matches()).isEmpty();
        assertThat(result.ambiguousClusters()).hasSize(1);
        assertThat(result.ambiguousClusters().get(0).members()).containsExactlyInAnyOrder(p.id(), q.id(), r.id());
        assertThat(result.discrepancies()).isEmpty();
    }

    /**
     * P star-connected to Q and R (P–Q adj, P–R adj); Q–R same-sign → |Q+R| large → not directly
     * adjacent. Component {P,Q,R} is still one connected component (via P) → AmbiguousCluster.
     */
    @Test
    void triangleClusterIsAmbiguous() {
        LedgerEntry p = usd("feed-p", "100.00", JAN_1);
        LedgerEntry q = usd("feed-q", "-100.02", JAN_1); // P–Q: |p+q|=0.02 ≤ 0.05 ✓
        LedgerEntry r = usd("feed-r", "-100.01", JAN_1); // P–R: |p+r|=0.01 ≤ 0.05 ✓; Q–R: same-sign, |q+r|=200 ✗

        MatchRunResult result =
                engine.run(MatchRunId.generate(), List.of(new AmountToleranceDateDriftRule(CFG)), List.of(p, q, r));

        assertThat(result.matches()).isEmpty();
        assertThat(result.ambiguousClusters()).hasSize(1);
        assertThat(result.ambiguousClusters().get(0).members()).containsExactlyInAnyOrder(p.id(), q.id(), r.id());
    }

    /**
     * A adjacent to B and C (via separate edges), B and C NOT adjacent (same feed).
     * Component {A,B,C}: one AmbiguousCluster, not a matched pair plus an orphan.
     */
    @Test
    void multipleCandidatesProducesAmbiguous() {
        LedgerEntry a = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b = usd("feed-b", "-100.02", JAN_1); // A–B: 0.02 ≤ 0.05
        LedgerEntry c = usd("feed-b", "-100.03", JAN_1); // A–C: 0.03 ≤ 0.05; B–C: same feed → not adjacent

        MatchRunResult result =
                engine.run(MatchRunId.generate(), List.of(new AmountToleranceDateDriftRule(CFG)), List.of(a, b, c));

        assertThat(result.matches()).isEmpty();
        assertThat(result.ambiguousClusters()).hasSize(1);
        assertThat(result.ambiguousClusters().get(0).members()).containsExactlyInAnyOrder(a.id(), b.id(), c.id());
    }

    /** Exactly one 2-node tolerance component → Matched pair, no cluster. */
    @Test
    void singleToleranceMatchIsMatchedNotAmbiguous() {
        LedgerEntry a = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b = usd("feed-b", "-100.03", JAN_1); // 0.03 ≤ 0.05 ✓

        MatchRunResult result =
                engine.run(MatchRunId.generate(), List.of(new AmountToleranceDateDriftRule(CFG)), List.of(a, b));

        assertThat(result.matches()).hasSize(1);
        assertThat(result.ambiguousClusters()).isEmpty();
        assertThat(result.discrepancies()).isEmpty();
        Set<LedgerEntryId> ids = matchedIdSet(result);
        assertThat(ids).containsExactlyInAnyOrder(a.id(), b.id());
    }

    /** AmbiguousCluster.members() is sorted ascending by UUID regardless of insertion order. */
    @Test
    void ambiguousClusterMembersSortedById() {
        // Force specific UUIDs so we can assert sort order
        UUID uuid1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID uuid2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID uuid3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

        // Create entries in REVERSE uuid order to prove the sort is applied.
        // Chain: e1–e2 adjacent (|e1+e2|=0.01), e2–e3 adjacent (|e2+e3|=0.01),
        // e1–e3 not directly adjacent (|e1+e3|=200.02, same sign). Component still {e1,e2,e3}.
        LedgerEntry e3 = entryWithId(uuid3, "feed-c", "100.02", JAN_1);
        LedgerEntry e2 = entryWithId(uuid2, "feed-b", "-100.01", JAN_1);
        LedgerEntry e1 = entryWithId(uuid1, "feed-a", "100.00", JAN_1);
        MatchRunResult result = engine.run(
                MatchRunId.generate(),
                List.of(new AmountToleranceDateDriftRule(CFG)),
                List.of(e3, e2, e1)); // inserted in reverse UUID order

        assertThat(result.ambiguousClusters()).hasSize(1);
        List<LedgerEntryId> members = result.ambiguousClusters().get(0).members();
        assertThat(members).containsExactly(LedgerEntryId.of(uuid1), LedgerEntryId.of(uuid2), LedgerEntryId.of(uuid3));
    }

    /** ExactPassCompletesBeforeTolerancePass: A–B exact; C,D tolerance-adjacent to A but same
     *  feed as each other → C and D end up UNMATCHED (not matching each other in tolerance pass). */
    @Test
    void exactPassCompletesBeforeTolerancePass() {
        LedgerEntry a = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b = usd("feed-b", "-100.00", JAN_1); // exact partner of A
        // C and D: feed-c (same feed as each other), within 0.10 tolerance of A but not exact
        ToleranceConfig wideCfg = new ToleranceConfig(null, new BigDecimal("0.10"), new BigDecimal("0.001"), 0);
        LedgerEntry c = usd("feed-c", "-100.05", JAN_1); // A–C: 0.05 ≤ 0.10; same feed as D
        LedgerEntry d = usd("feed-c", "-100.08", JAN_1); // A–D: 0.08 ≤ 0.10; C–D: same feed → not adjacent

        MatchRunResult result = engine.run(
                MatchRunId.generate(),
                List.of(new ExactAmountAndDateRule(), new AmountToleranceDateDriftRule(wideCfg)),
                List.of(a, b, c, d));

        // Exact pass: A–B matched
        assertThat(result.matches()).hasSize(1);
        assertThat(matchedIdSet(result)).containsExactlyInAnyOrder(a.id(), b.id());
        // Tolerance pass: C and D are isolated (same feed → not adjacent to each other; A is gone)
        assertThat(result.ambiguousClusters()).isEmpty();
        assertThat(result.discrepancies()).hasSize(2);
        Set<LedgerEntryId> unmatchedIds =
                result.discrepancies().stream().map(Discrepancy::entryId).collect(Collectors.toSet());
        assertThat(unmatchedIds).containsExactlyInAnyOrder(c.id(), d.id());
    }

    /**
     * Under SUM_TO_ZERO axis, two same-sign entries from different feeds must NOT be adjacent even
     * when their magnitudes differ by less than absoluteTolerance. They are both debits (or both
     * credits) and cannot form a debit/credit pair: |a+b| = ~200 >> tolerance.
     */
    @Test
    void sumToZeroAxisRejectsSameSignNearValues() {
        // CFG uses SUM_TO_ZERO (default via 4-arg constructor)
        LedgerEntry a = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b = usd("feed-b", "99.97", JAN_1); // magnitude diff 0.03 ≤ 0.05, but |a+b|=199.97

        assertThat(rule.neighbors(a, List.of(a, b))).doesNotContain(b);
        assertThat(rule.neighbors(b, List.of(a, b))).doesNotContain(a);

        LedgerEntry c = usd("feed-c", "-100.00", JAN_1);
        LedgerEntry d = usd("feed-d", "-99.97", JAN_1);

        assertThat(rule.neighbors(c, List.of(c, d))).doesNotContain(d);
        assertThat(rule.neighbors(d, List.of(c, d))).doesNotContain(c);
    }

    /**
     * Under DIFFERENCE axis, two same-sign entries from different feeds with close amounts ARE
     * adjacent: |a−b| = 0.03 ≤ 0.05. This is the same-sign bank/ledger convention where both
     * feeds record the same value with the same sign.
     */
    @Test
    void differenceAxisMatchesSameSignNearValues() {
        ToleranceConfig diffCfg =
                new ToleranceConfig(null, new BigDecimal("0.05"), new BigDecimal("0.001"), 0, MatchingAxis.DIFFERENCE);
        AmountToleranceDateDriftRule diffRule = new AmountToleranceDateDriftRule(diffCfg);

        LedgerEntry a = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b = usd("feed-b", "99.97", JAN_1); // |a−b| = 0.03 ≤ 0.05

        assertThat(diffRule.neighbors(a, List.of(a, b))).contains(b);
        assertThat(diffRule.neighbors(b, List.of(a, b))).contains(a);
    }

    /**
     * Under SUM_TO_ZERO axis, an opposite-sign debit/credit pair within tolerance IS adjacent:
     * |a+b| = 0.03 ≤ 0.05. This is the standard two-feed reconciliation convention.
     */
    @Test
    void sumToZeroAxisMatchesOppositeSign() {
        // CFG uses SUM_TO_ZERO
        LedgerEntry a = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b = usd("feed-b", "-99.97", JAN_1); // |a+b| = 0.03 ≤ 0.05

        assertThat(rule.neighbors(a, List.of(a, b))).contains(b);
        assertThat(rule.neighbors(b, List.of(a, b))).contains(a);
    }

    /**
     * An exact match under a given axis must also satisfy the tolerance predicate under the SAME
     * axis — the two rules must agree on what "equal amount" means. If this fails, the exact rule
     * and the tolerance rule are incoherent about the matching quantity.
     */
    @Test
    void exactMatchImpliesWithinTolerance() {
        // SUM_TO_ZERO: a+b=0 is exact; tolerance sees |a+b|=0 ≤ any non-negative tolerance
        ToleranceConfig sumCfg =
                new ToleranceConfig(null, new BigDecimal("0.01"), new BigDecimal("0.001"), 0, MatchingAxis.SUM_TO_ZERO);
        LedgerEntry a1 = usd("feed-a", "100.00", JAN_1);
        LedgerEntry b1 = usd("feed-b", "-100.00", JAN_1);
        assertThat(new ExactAmountAndDateRule(MatchingAxis.SUM_TO_ZERO).neighbors(a1, List.of(a1, b1)))
                .as("exact rule must report b1 as neighbor of a1 under SUM_TO_ZERO")
                .contains(b1);
        assertThat(new AmountToleranceDateDriftRule(sumCfg).neighbors(a1, List.of(a1, b1)))
                .as("tolerance rule must also report b1 as neighbor (exact implies within tolerance)")
                .contains(b1);

        // DIFFERENCE: a−b=0 is exact; tolerance sees |a−b|=0 ≤ any non-negative tolerance
        ToleranceConfig diffCfg =
                new ToleranceConfig(null, new BigDecimal("0.01"), new BigDecimal("0.001"), 0, MatchingAxis.DIFFERENCE);
        LedgerEntry a2 = usd("feed-c", "100.00", JAN_1);
        LedgerEntry b2 = usd("feed-d", "100.00", JAN_1);
        assertThat(new ExactAmountAndDateRule(MatchingAxis.DIFFERENCE).neighbors(a2, List.of(a2, b2)))
                .as("exact rule must report b2 as neighbor of a2 under DIFFERENCE")
                .contains(b2);
        assertThat(new AmountToleranceDateDriftRule(diffCfg).neighbors(a2, List.of(a2, b2)))
                .as("tolerance rule must also report b2 as neighbor (exact implies within tolerance)")
                .contains(b2);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static LedgerEntry usd(String feedId, String amount, LocalDate date) {
        return entry(feedId, amount, USD, date);
    }

    private static LedgerEntry entry(String feedId, String amount, Currency currency, LocalDate date) {
        return LedgerEntry.create(feedId, date, Money.of(new BigDecimal(amount), currency), "", "", UUID.randomUUID());
    }

    private static LedgerEntry entryWithId(UUID id, String feedId, String amount, LocalDate date) {
        return new LedgerEntry(
                LedgerEntryId.of(id), feedId, date, Money.of(new BigDecimal(amount), USD), "", "", UUID.randomUUID());
    }

    private static Set<LedgerEntryId> matchedIdSet(MatchRunResult result) {
        Set<LedgerEntryId> ids = new java.util.HashSet<>(result.matches().keySet());
        ids.addAll(result.matches().values());
        return ids;
    }

    private void assertSymmetric(LedgerEntry a, LedgerEntry b, boolean expectedAdjacent) {
        List<LedgerEntry> poolAB = List.of(a, b);
        boolean aSeesB = rule.neighbors(a, poolAB).contains(b);
        boolean bSeesA = rule.neighbors(b, poolAB).contains(a);
        assertThat(aSeesB).as("neighbors(%s,[%s]) adjacency", a.id(), b.id()).isEqualTo(expectedAdjacent);
        assertThat(bSeesA).as("neighbors(%s,[%s]) adjacency", b.id(), a.id()).isEqualTo(expectedAdjacent);
        assertThat(aSeesB).as("relation must be symmetric").isEqualTo(bSeesA);
    }
}
