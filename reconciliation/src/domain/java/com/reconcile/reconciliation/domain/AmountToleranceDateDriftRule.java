package com.reconcile.reconciliation.domain;

import com.reconcile.ledger.domain.LedgerEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Matches entries from different feeds when the amount is within a configured tolerance AND the
 * date difference is within maxDateDriftDays.
 *
 * <p>Amount check (OR semantics — either clause suffices):
 *
 * <pre>
 *   diff  = config.axis().amountDiff(a, b)        // |a+b| or |a−b|, per MatchingAxis
 *   denom = max(|a|, |b|)
 *   diff ≤ absoluteTolerance  OR  diff/denom ≤ percentageTolerance
 * </pre>
 *
 * <p>The numerator is determined by {@link MatchingAxis}: {@code SUM_TO_ZERO} uses {@code |a + b|}
 * (pair sums to zero — opposite-sign debit/credit); {@code DIFFERENCE} uses {@code |a − b|}
 * (pair is approximately equal — same-sign bank/ledger). The denominator {@code max(|a|,|b|)} is
 * sign-independent and unchanged. Percentage uses HALF_EVEN, scale 10. When
 * {@code max(|a|,|b|) == 0} the percentage clause is skipped and absolute governs.
 *
 * <p>The relation is symmetric: both numerator forms commute over the (a,b) pair.
 */
public final class AmountToleranceDateDriftRule implements MatchRule {

    public static final String RULE_ID = "AMOUNT_TOLERANCE_DATE_DRIFT";

    private final ToleranceConfig config;

    public AmountToleranceDateDriftRule(ToleranceConfig config) {
        this.config = config;
    }

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public List<LedgerEntry> neighbors(LedgerEntry candidate, List<LedgerEntry> pool) {
        return pool.stream()
                .filter(other -> !other.id().equals(candidate.id()))
                .filter(other -> !other.feedId().equals(candidate.feedId()))
                .filter(other ->
                        other.amount().currency().equals(candidate.amount().currency()))
                .filter(other -> isAmountWithinTolerance(candidate, other))
                .filter(other -> isDateWithinDrift(candidate, other))
                .sorted(Comparator.comparing(e -> e.id().value()))
                .toList();
    }

    private boolean isAmountWithinTolerance(LedgerEntry a, LedgerEntry b) {
        BigDecimal diff = config.axis().amountDiff(a.amount(), b.amount());

        if (diff.compareTo(config.absoluteTolerance()) <= 0) return true;

        BigDecimal aMag = a.amount().amount().abs();
        BigDecimal bMag = b.amount().amount().abs();
        BigDecimal denom = aMag.max(bMag);
        if (denom.signum() == 0) return true;

        BigDecimal pct = diff.divide(denom, 10, RoundingMode.HALF_EVEN);
        return pct.compareTo(config.percentageTolerance()) <= 0;
    }

    private boolean isDateWithinDrift(LedgerEntry a, LedgerEntry b) {
        long drift = ChronoUnit.DAYS.between(a.entryDate(), b.entryDate());
        return Math.abs(drift) <= config.maxDateDriftDays();
    }
}
