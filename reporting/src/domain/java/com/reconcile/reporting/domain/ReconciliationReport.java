package com.reconcile.reporting.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconciliationReport(
        UUID runId,
        Instant generatedAt,
        int matchedCount,
        int unmatchedCount,
        List<DiscrepancySummary> discrepancies) {

    public record DiscrepancySummary(UUID entryId, String reason) {}
}
