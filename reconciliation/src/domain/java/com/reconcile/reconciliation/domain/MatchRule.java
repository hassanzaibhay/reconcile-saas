package com.reconcile.reconciliation.domain;

import com.reconcile.ledger.domain.LedgerEntry;
import java.util.List;

/**
 * Sealed contract for matching rules. All implementations must be deterministic: same inputs +
 * same rule set → identical match decisions. New rules are added by extending {@code permits}.
 *
 * <p>The engine calls {@code neighbors()} for every node in the frozen pool to build an
 * undirected adjacency graph, then partitions by connected components. Size-2 components become
 * matched pairs; size-≥3 components become ambiguous clusters; isolated nodes flow to the next
 * pass.
 */
public sealed interface MatchRule permits ExactAmountAndDateRule, AmountToleranceDateDriftRule {

    String ruleId();

    /**
     * Returns all entries in {@code pool} that this rule considers adjacent to {@code candidate}.
     * Must NOT include {@code candidate} itself. Must be deterministic: same inputs → same list.
     * The relation must be symmetric: if b ∈ neighbors(a, pool) then a ∈ neighbors(b, pool).
     */
    List<LedgerEntry> neighbors(LedgerEntry candidate, List<LedgerEntry> pool);
}
