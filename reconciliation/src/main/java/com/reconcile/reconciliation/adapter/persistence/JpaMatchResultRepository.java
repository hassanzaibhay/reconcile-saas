package com.reconcile.reconciliation.adapter.persistence;

import com.reconcile.reconciliation.application.MatchResultRepository;
import com.reconcile.reconciliation.domain.MatchPair;
import com.reconcile.reconciliation.domain.MatchRunId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaMatchResultRepository implements MatchResultRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void saveAll(MatchRunId runId, List<MatchPair> pairs) {
        for (MatchPair pair : pairs) {
            MatchResultEntity entity = new MatchResultEntity();
            entity.id = UUID.randomUUID();
            entity.matchRunId = runId.value();
            entity.leftEntryId = pair.left().value();
            entity.rightEntryId = pair.right().value();
            entity.ruleId = pair.ruleId();
            entity.matchedAt = Instant.now();
            em.persist(entity);
        }
    }
}
