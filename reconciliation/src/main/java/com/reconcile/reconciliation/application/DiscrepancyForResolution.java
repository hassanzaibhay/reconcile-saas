package com.reconcile.reconciliation.application;

import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.reconciliation.domain.AmbiguousCluster;
import com.reconcile.reconciliation.domain.MatchRunId;

/**
 * Read-side view of a discrepancy for resolution. {@code cluster} is populated only for AMBIGUOUS
 * discrepancies; {@code unmatchedEntryId} only for UNMATCHED ones.
 */
public record DiscrepancyForResolution(
        java.util.UUID id,
        MatchRunId matchRunId,
        String type,
        LedgerEntryId unmatchedEntryId,
        AmbiguousCluster cluster,
        String status) {}
