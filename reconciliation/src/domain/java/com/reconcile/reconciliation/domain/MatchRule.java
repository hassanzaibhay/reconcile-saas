package com.reconcile.reconciliation.domain;

import com.reconcile.ledger.domain.LedgerEntry;
import java.util.Optional;

/**
 * Sealed contract for matching rules. All implementations must be deterministic: same inputs +
 * same rule set → identical match decisions. New rules are added by extending {@code permits}.
 */
public sealed interface MatchRule permits ExactAmountAndDateRule {

    String ruleId();

    /**
     * Attempt to match {@code candidate} against one entry in {@code pool}.
     *
     * @return the matched entry's ID wrapped in Optional, or empty if no match.
     */
    Optional<LedgerEntry> match(LedgerEntry candidate, Iterable<LedgerEntry> pool);
}
