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

            Instant matchedAt = Instant.now();
            em.persist(matchedEntry(pair.left().value(), entity.id, matchedAt));
            em.persist(matchedEntry(pair.right().value(), entity.id, matchedAt));
        }
    }

    private MatchedEntryEntity matchedEntry(UUID ledgerEntryId, UUID matchResultId, Instant matchedAt) {
        MatchedEntryEntity entity = new MatchedEntryEntity();
        entity.ledgerEntryId = ledgerEntryId;
        entity.matchResultId = matchResultId;
        entity.matchedAt = matchedAt;
        return entity;
    }
}
