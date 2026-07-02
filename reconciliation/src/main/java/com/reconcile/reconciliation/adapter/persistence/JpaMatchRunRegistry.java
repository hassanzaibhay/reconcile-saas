package com.reconcile.reconciliation.adapter.persistence;

import com.reconcile.reconciliation.application.MatchRunRegistry;
import com.reconcile.reconciliation.domain.MatchRunId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
class JpaMatchRunRegistry implements MatchRunRegistry {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void create(MatchRunId runId) {
        MatchRunEntity entity = new MatchRunEntity();
        entity.id = runId.value();
        entity.status = "RUNNING";
        entity.startedAt = Instant.now();
        entity.matchedCount = 0;
        entity.unmatchedCount = 0;
        em.persist(entity);
    }

    @Override
    public void complete(MatchRunId runId, int matchedCount, int unmatchedCount) {
        MatchRunEntity entity = em.find(MatchRunEntity.class, runId.value());
        if (entity != null) {
            entity.status = "COMPLETED";
            entity.completedAt = Instant.now();
            entity.matchedCount = matchedCount;
            entity.unmatchedCount = unmatchedCount;
        }
    }
}
