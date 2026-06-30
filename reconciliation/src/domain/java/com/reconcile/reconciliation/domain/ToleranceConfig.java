package com.reconcile.reconciliation.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Per-tenant tolerance parameters for the AmountToleranceDateDriftRule. Stored in the tenant
 * schema (reconciliation_config table, sub-slice 1). This record is pure domain — no Spring, no
 * JPA.
 *
 * <p>{@code axis} controls which sign convention the matching rules use. See {@link MatchingAxis}.
 */
public record ToleranceConfig(
        UUID id,
        BigDecimal absoluteTolerance,
        BigDecimal percentageTolerance,
        int maxDateDriftDays,
        MatchingAxis axis) {

    /** Convenience constructor: axis defaults to {@link MatchingAxis#SUM_TO_ZERO}. */
    public ToleranceConfig(
            UUID id, BigDecimal absoluteTolerance, BigDecimal percentageTolerance, int maxDateDriftDays) {
        this(id, absoluteTolerance, percentageTolerance, maxDateDriftDays, MatchingAxis.SUM_TO_ZERO);
    }

    public static ToleranceConfig defaults() {
        return new ToleranceConfig(null, new BigDecimal("0.01"), new BigDecimal("0.001"), 0, MatchingAxis.SUM_TO_ZERO);
    }
}
