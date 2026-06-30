package com.reconcile.reconciliation.domain;

import com.reconcile.shared.domain.Money;
import java.math.BigDecimal;

/**
 * Defines the sign convention used by both the exact and tolerance matching rules for a given
 * tenant configuration.
 *
 * <ul>
 *   <li>{@link #SUM_TO_ZERO} — the reconciling pair sums to zero (opposite-sign debit/credit).
 *       Numerator: {@code |a + b|}. This is the default and matches the {@code ExactAmountAndDateRule}
 *       behaviour: {@code a.add(b).isZero()}.
 *   <li>{@link #DIFFERENCE} — the reconciling pair is approximately equal (same-sign bank/ledger).
 *       Numerator: {@code |a − b|}. Exact match means {@code a.subtract(b).isZero()}.
 * </ul>
 *
 * <p>Both rules compute their matched quantity through {@link #amountDiff} so they can never
 * disagree about what "equal amount" means.
 */
public enum MatchingAxis {

    /** Pair sums to zero. Numerator: |a + b|. Default. */
    SUM_TO_ZERO,

    /** Pair is approximately equal. Numerator: |a − b|. */
    DIFFERENCE;

    /**
     * Returns the axis-specific distance from a perfect match. Zero means exact match;
     * positive means residual discrepancy. The denominator for percentage tolerance is
     * {@code max(|a|, |b|)} and is computed separately (sign-independent, unchanged by axis).
     */
    public BigDecimal amountDiff(Money a, Money b) {
        return switch (this) {
            case SUM_TO_ZERO -> a.add(b).amount().abs();
            case DIFFERENCE -> a.subtract(b).amount().abs();
        };
    }

    /** Returns true when the pair is an exact match under this axis (diff == 0). */
    public boolean isExactMatch(Money a, Money b) {
        return amountDiff(a, b).compareTo(BigDecimal.ZERO) == 0;
    }
}
