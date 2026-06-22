package com.reconcile.reconciliation.domain;

import com.reconcile.ledger.domain.LedgerEntry;
import java.util.Optional;

/**
 * Matches two entries across different feeds when they have equal amount magnitude, opposite signs,
 * and the same date. This is the canonical exact-match rule for bank-to-processor reconciliation.
 */
public final class ExactAmountAndDateRule implements MatchRule {

    public static final String RULE_ID = "EXACT_AMOUNT_AND_DATE";

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public Optional<LedgerEntry> match(LedgerEntry candidate, Iterable<LedgerEntry> pool) {
        for (LedgerEntry other : pool) {
            if (other.id().equals(candidate.id())) continue;
            if (!other.feedId().equals(candidate.feedId())
                    && other.entryDate().equals(candidate.entryDate())
                    && candidate.amount().add(other.amount()).isZero()) {
                return Optional.of(other);
            }
        }
        return Optional.empty();
    }
}
