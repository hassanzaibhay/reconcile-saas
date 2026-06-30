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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DefaultMatchingEngine's uniform graph-partition algorithm. No Spring context,
 * no Testcontainers. TenantContext is set so MatchRunCompletedEvent can capture it.
 */
class MatchingEngineTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final LocalDate JAN_1 = LocalDate.of(2025, 1, 1);
    private static final LocalDate JAN_2 = LocalDate.of(2025, 1, 2);

    private DefaultMatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultMatchingEngine(decision -> {}, event -> {});
        TenantContext.set(TenantId.of(UUID.randomUUID()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------------------
    // Phasing: exact pass must complete before tolerance pass
    // -------------------------------------------------------------------------

    /**
     * THE defect regression. X is within tolerance of Y, but Y and Z are an exact pair. The exact
     * pass must claim Y–Z first; the tolerance pass sees only X in the residual and produces
     * UNMATCHED. If the engine were greedy per-candidate, X could steal Y from Z.
     */
    @Test
    void toleranceMustNotConsumeExactPair() {
        // Y–Z: exact pair (+100 / −100)
        LedgerEntry y = entry("feed-y", "100.00", JAN_1);
        LedgerEntry z = entry("feed-z", "-100.00", JAN_1);
        // X: within 0.01 absolute tolerance of Y but NOT exact (−100.01 ≠ −100.00)
        LedgerEntry x = entry("feed-x", "-100.01", JAN_1);

        ToleranceConfig cfg = new ToleranceConfig(null, new BigDecimal("0.01"), new BigDecimal("0.001"), 0);
        List<MatchRule> rules = List.of(new ExactAmountAndDateRule(), new AmountToleranceDateDriftRule(cfg));

        MatchRunResult result = engine.run(MatchRunId.generate(), rules, List.of(x, y, z));

        assertThat(result.matches()).hasSize(1);
        Set<LedgerEntryId> matchedIds = matchedIdSet(result);
        assertThat(matchedIds).containsExactlyInAnyOrder(y.id(), z.id());
        assertThat(result.ambiguousClusters()).isEmpty();
        assertThat(result.discrepancies()).hasSize(1);
        assertThat(result.discrepancies().get(0).entryId()).isEqualTo(x.id());
    }

    /**
     * Three entries across three feeds that all exactly match entry A: forms one connected
     * component of size 3 → AmbiguousCluster. No silent greedy pairing.
     */
    @Test
    void exactThreeWayContentionIsAmbiguous() {
        // A(+100) is exact-adjacent to B(−100) and C(−100); B and C are not adjacent to each other
        LedgerEntry a = entry("feed-a", "100.00", JAN_1);
        LedgerEntry b = entry("feed-b", "-100.00", JAN_1);
        LedgerEntry c = entry("feed-c", "-100.00", JAN_1);

        MatchRunResult result =
                engine.run(MatchRunId.generate(), List.of(new ExactAmountAndDateRule()), List.of(a, b, c));

        assertThat(result.matches()).isEmpty();
        assertThat(result.discrepancies()).isEmpty();
        assertThat(result.ambiguousClusters()).hasSize(1);
        assertThat(result.ambiguousClusters().get(0).members()).containsExactlyInAnyOrder(a.id(), b.id(), c.id());
    }

    /** Two disjoint exact pairs: {A–B} and {C–D} with no cross edges → two matched pairs. */
    @Test
    void twoDisjointPairsStayMatched() {
        LedgerEntry a = entry("feed-a", "100.00", JAN_1);
        LedgerEntry b = entry("feed-b", "-100.00", JAN_1);
        LedgerEntry c = entry("feed-c", "200.00", JAN_2);
        LedgerEntry d = entry("feed-d", "-200.00", JAN_2);

        MatchRunResult result =
                engine.run(MatchRunId.generate(), List.of(new ExactAmountAndDateRule()), List.of(a, b, c, d));

        assertThat(result.matches()).hasSize(2);
        assertThat(result.ambiguousClusters()).isEmpty();
        assertThat(result.discrepancies()).isEmpty();
        assertThat(matchedIdSet(result)).containsExactlyInAnyOrder(a.id(), b.id(), c.id(), d.id());
    }

    /**
     * Shuffle the input; assert the match-pair set, unmatched-id set, and ambiguous cluster
     * member-sets are identical across both runs. Tests that the partition is a function of the
     * edge set alone, independent of input order.
     */
    @Test
    void shuffledInputDeterminism() {
        // 4 exact pairs
        LedgerEntry e1 = entry("feed-a1", "10.00", LocalDate.of(2025, 1, 5));
        LedgerEntry e2 = entry("feed-b1", "-10.00", LocalDate.of(2025, 1, 5));
        LedgerEntry e3 = entry("feed-a2", "20.00", LocalDate.of(2025, 1, 6));
        LedgerEntry e4 = entry("feed-b2", "-20.00", LocalDate.of(2025, 1, 6));
        LedgerEntry e5 = entry("feed-a3", "30.00", LocalDate.of(2025, 1, 7));
        LedgerEntry e6 = entry("feed-b3", "-30.00", LocalDate.of(2025, 1, 7));
        LedgerEntry e7 = entry("feed-a4", "40.00", LocalDate.of(2025, 1, 8));
        LedgerEntry e8 = entry("feed-b4", "-40.00", LocalDate.of(2025, 1, 8));

        // P–Q–R path in tolerance (absolute 0.05); not exact.
        // |p+q|=0.03 ≤ 0.05; |q+r|=0.03 ≤ 0.05; |p+r|=200.06 (same-sign) → P–R not adjacent.
        LocalDate jan10 = LocalDate.of(2025, 1, 10);
        LedgerEntry p = entry("feed-p", "100.00", jan10);
        LedgerEntry q = entry("feed-q", "-100.03", jan10);
        LedgerEntry r = entry("feed-r", "100.06", jan10); // opposite sign to Q → Q–R adjacent; same sign as P → P–R not

        // 2 isolated unmatched (no counterpart anywhere)
        LocalDate jan15 = LocalDate.of(2025, 1, 15);
        LedgerEntry s1 = entry("feed-s1", "500.00", jan15);
        LedgerEntry s2 = entry("feed-s2", "600.00", jan15);

        ToleranceConfig cfg = new ToleranceConfig(null, new BigDecimal("0.05"), new BigDecimal("0.0001"), 0);
        List<MatchRule> rules = List.of(new ExactAmountAndDateRule(), new AmountToleranceDateDriftRule(cfg));

        List<LedgerEntry> entries = new ArrayList<>(List.of(e1, e2, e3, e4, e5, e6, e7, e8, p, q, r, s1, s2));
        MatchRunResult result1 = engine.run(MatchRunId.generate(), rules, new ArrayList<>(entries));

        Collections.shuffle(entries, new Random(12345));
        MatchRunResult result2 = engine.run(MatchRunId.generate(), rules, entries);

        assertThat(result1.matches()).isEqualTo(result2.matches());

        Set<List<LedgerEntryId>> clusters1 = result1.ambiguousClusters().stream()
                .map(AmbiguousCluster::members)
                .collect(Collectors.toSet());
        Set<List<LedgerEntryId>> clusters2 = result2.ambiguousClusters().stream()
                .map(AmbiguousCluster::members)
                .collect(Collectors.toSet());
        assertThat(clusters1).isEqualTo(clusters2);

        Set<LedgerEntryId> unmatched1 =
                result1.discrepancies().stream().map(Discrepancy::entryId).collect(Collectors.toSet());
        Set<LedgerEntryId> unmatched2 =
                result2.discrepancies().stream().map(Discrepancy::entryId).collect(Collectors.toSet());
        assertThat(unmatched1).isEqualTo(unmatched2);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static LedgerEntry entry(String feedId, String amount, LocalDate date) {
        return LedgerEntry.create(feedId, date, Money.of(new BigDecimal(amount), USD), "", "", UUID.randomUUID());
    }

    private static Set<LedgerEntryId> matchedIdSet(MatchRunResult result) {
        Set<LedgerEntryId> ids = new java.util.HashSet<>(result.matches().keySet());
        ids.addAll(result.matches().values());
        return ids;
    }
}
