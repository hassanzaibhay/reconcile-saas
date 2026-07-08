package com.reconcile.reconciliation.adapter.persistence;

import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.reconciliation.application.ResolutionRepository;
import com.reconcile.reconciliation.domain.Pairing;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class JpaResolutionRepository implements ResolutionRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void recordUnmatchedReviewed(UUID discrepancyId, UUID resolutionId, String resolvedBy) {
        flipToResolved(discrepancyId);
        em.persist(resolutionEntity(discrepancyId, resolutionId, "UNMATCHED_REVIEWED", resolvedBy));
    }

    @Override
    public void recordClusterResolution(
            UUID discrepancyId,
            UUID resolutionId,
            String resolvedBy,
            List<Pairing> pairings,
            List<LedgerEntryId> leftUnmatched) {
        flipToResolved(discrepancyId);
        em.persist(resolutionEntity(discrepancyId, resolutionId, "CLUSTER", resolvedBy));

        for (Pairing pairing : pairings) {
            ResolutionPairingEntity pairingEntity = new ResolutionPairingEntity();
            pairingEntity.resolutionId = resolutionId;
            pairingEntity.leftEntryId = pairing.a().value();
            pairingEntity.rightEntryId = pairing.b().value();
            em.persist(pairingEntity);
        }

        for (LedgerEntryId entryId : leftUnmatched) {
            ResolutionUnmatchedEntity unmatchedEntity = new ResolutionUnmatchedEntity();
            unmatchedEntity.resolutionId = resolutionId;
            unmatchedEntity.ledgerEntryId = entryId.value();
            em.persist(unmatchedEntity);
        }
    }

    private ResolutionEntity resolutionEntity(UUID discrepancyId, UUID resolutionId, String kind, String resolvedBy) {
        ResolutionEntity resolution = new ResolutionEntity();
        resolution.id = resolutionId;
        resolution.discrepancyId = discrepancyId;
        resolution.kind = kind;
        resolution.resolvedBy = resolvedBy;
        resolution.resolvedAt = Instant.now();
        return resolution;
    }

    private void flipToResolved(UUID discrepancyId) {
        DiscrepancyEntity entity = em.find(DiscrepancyEntity.class, discrepancyId);
        if (!"OPEN".equals(entity.status)) {
            throw new ObjectOptimisticLockingFailureException(DiscrepancyEntity.class, discrepancyId);
        }
        entity.status = "RESOLVED";
    }
}
