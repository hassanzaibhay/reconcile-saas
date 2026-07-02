package com.reconcile.reconciliation.adapter.persistence;

import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.reconciliation.application.DiscrepancyRepository;
import com.reconcile.reconciliation.domain.AmbiguousCluster;
import com.reconcile.reconciliation.domain.Discrepancy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
class JpaDiscrepancyRepository implements DiscrepancyRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Discrepancy discrepancy) {
        switch (discrepancy) {
            case Discrepancy.Unmatched u -> persistUnmatched(u);
            case Discrepancy.Ambiguous a -> persistAmbiguous(a);
        }
    }

    private void persistUnmatched(Discrepancy.Unmatched u) {
        DiscrepancyEntity entity = new DiscrepancyEntity();
        entity.id = u.id();
        entity.matchRunId = u.matchRunId().value();
        entity.type = "UNMATCHED";
        entity.unmatchedEntryId = u.entryId().value();
        entity.createdAt = Instant.now();
        em.persist(entity);
    }

    private void persistAmbiguous(Discrepancy.Ambiguous a) {
        DiscrepancyEntity entity = new DiscrepancyEntity();
        entity.id = a.id();
        entity.matchRunId = a.matchRunId().value();
        entity.type = "AMBIGUOUS";
        entity.unmatchedEntryId = null;
        entity.createdAt = Instant.now();
        em.persist(entity);

        AmbiguousCluster cluster = a.cluster();
        for (LedgerEntryId memberId : cluster.members()) {
            AmbiguousClusterMemberEntity member = new AmbiguousClusterMemberEntity();
            member.discrepancyId = a.id();
            member.ledgerEntryId = memberId.value();
            em.persist(member);
        }
    }
}
