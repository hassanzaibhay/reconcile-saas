package com.reconcile.reconciliation.application;

import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.reconciliation.domain.Pairing;
import java.util.List;
import java.util.UUID;

public interface ResolutionRepository {

    /**
     * Flips the discrepancy to RESOLVED and records an UNMATCHED_REVIEWED resolution. Throws
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException} if the discrepancy is
     * not currently OPEN.
     */
    void recordUnmatchedReviewed(UUID discrepancyId, UUID resolutionId, String resolvedBy);

    /**
     * Flips the discrepancy to RESOLVED and records a CLUSTER resolution with its pairings and
     * unmatched residual. Throws {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * if the discrepancy is not currently OPEN.
     */
    void recordClusterResolution(
            UUID discrepancyId,
            UUID resolutionId,
            String resolvedBy,
            List<Pairing> pairings,
            List<LedgerEntryId> leftUnmatched);
}
