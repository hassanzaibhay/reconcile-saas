package com.reconcile.reconciliation.domain;

import com.reconcile.ledger.domain.LedgerEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * Matches entries across different feeds when the amount is an exact match under the configured
 * {@link MatchingAxis}, same currency, and identical date. Returns ALL exact neighbors so the
 * engine can detect multi-feed contention (3+ entries that all exactly match each other →
 * ambiguous cluster, not silent greedy pairing).
 *
 * <p>Default axis is {@link MatchingAxis#SUM_TO_ZERO}: pair sums to zero (opposite-sign
 * debit/credit). Use {@link MatchingAxis#DIFFERENCE} for same-sign bank/ledger feeds where the
 * pair is approximately equal.
 */
public final class ExactAmountAndDateRule implements MatchRule {

    public static final String RULE_ID = "EXACT_AMOUNT_AND_DATE";

    private final MatchingAxis axis;

    /** Constructs with {@link MatchingAxis#SUM_TO_ZERO} — backward-compatible default. */
    public ExactAmountAndDateRule() {
        this(MatchingAxis.SUM_TO_ZERO);
    }

    public ExactAmountAndDateRule(MatchingAxis axis) {
        this.axis = axis;
    }

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public List<LedgerEntry> neighbors(LedgerEntry candidate, List<LedgerEntry> pool) {
        List<LedgerEntry> result = new ArrayList<>();
        for (LedgerEntry other : pool) {
            if (other.id().equals(candidate.id())) continue;
            if (!other.feedId().equals(candidate.feedId())
                    && other.amount().currency().equals(candidate.amount().currency())
                    && other.entryDate().equals(candidate.entryDate())
                    && axis.isExactMatch(candidate.amount(), other.amount())) {
                result.add(other);
            }
        }
        return result;
    }
}
