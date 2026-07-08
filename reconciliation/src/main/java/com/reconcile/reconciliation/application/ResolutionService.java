package com.reconcile.reconciliation.application;

import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.reconciliation.domain.ClusterResolution;
import com.reconcile.reconciliation.domain.MatchPair;
import com.reconcile.reconciliation.domain.Pairing;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves an operator's decision on an open discrepancy: accept an UNMATCHED discrepancy as
 * reviewed, or partition an AMBIGUOUS cluster into pairings + an explicit unmatched residual.
 */
@Service
public class ResolutionService {

    public static final String MANUAL_RESOLUTION_RULE_ID = "MANUAL_RESOLUTION";

    private final ResolutionRepository resolutionRepository;
    private final MatchResultRepository matchResultRepository;
    private final DiscrepancyQueryPort discrepancyQueryPort;

    public ResolutionService(
            ResolutionRepository resolutionRepository,
            MatchResultRepository matchResultRepository,
            DiscrepancyQueryPort discrepancyQueryPort) {
        this.resolutionRepository = resolutionRepository;
        this.matchResultRepository = matchResultRepository;
        this.discrepancyQueryPort = discrepancyQueryPort;
    }

    public Optional<DiscrepancyForResolution> find(UUID discrepancyId) {
        return discrepancyQueryPort.loadDetail(discrepancyId);
    }

    @Transactional
    public void resolveUnmatched(UUID discrepancyId, String resolvedBy) {
        resolutionRepository.recordUnmatchedReviewed(discrepancyId, UUID.randomUUID(), resolvedBy);
    }

    @Transactional
    public void resolveCluster(
            DiscrepancyForResolution discrepancy,
            List<Pairing> pairings,
            List<LedgerEntryId> leftUnmatched,
            String resolvedBy) {
        ClusterResolution resolution =
                ClusterResolution.of(discrepancy.cluster(), discrepancy.id(), pairings, leftUnmatched);

        for (Pairing pairing : resolution.pairings()) {
            matchResultRepository.saveAll(
                    discrepancy.matchRunId(),
                    List.of(new MatchPair(pairing.a(), pairing.b(), MANUAL_RESOLUTION_RULE_ID)));
        }

        resolutionRepository.recordClusterResolution(
                discrepancy.id(), UUID.randomUUID(), resolvedBy, resolution.pairings(), resolution.leftUnmatched());
    }
}
