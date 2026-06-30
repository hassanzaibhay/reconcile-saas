package com.reconcile.reconciliation.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record ToleranceConfig(
        UUID id,
        BigDecimal absoluteTolerance,
        BigDecimal percentageTolerance,
        int maxDateDriftDays,
        // SLICE-1 MIGRATION: axis VARCHAR(20) NOT NULL DEFAULT 'SUM_TO_ZERO' in V6 migration.
        // When feat/tolerance-config merges, remove this comment.
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
