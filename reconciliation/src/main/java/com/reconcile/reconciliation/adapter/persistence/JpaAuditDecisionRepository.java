package com.reconcile.reconciliation.adapter.persistence;

import com.reconcile.reconciliation.application.AuditDecision;
import com.reconcile.reconciliation.application.AuditDecisionRepository;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaAuditDecisionRepository implements AuditDecisionRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(AuditDecision decision) {
        AuditDecisionEntity entity = new AuditDecisionEntity();
        entity.id = decision.id();
        entity.matchRunId = decision.matchRunId().value();
        entity.ruleId = decision.ruleId();
        entity.entryId = decision.entryId().value();
        entity.decision = decision.decision();
        entity.reason = decision.reason();
        entity.decidedAt = decision.decidedAt();
        entity.decidedBy = decision.decidedBy();
        em.persist(entity);
    }

    @Entity
    @Table(name = "audit_decision")
    static class AuditDecisionEntity {
        @Id UUID id;
        @Column(name = "match_run_id") UUID matchRunId;
        @Column(name = "rule_id") String ruleId;
        @Column(name = "entry_id") UUID entryId;
        @Column(name = "decision") String decision;
        @Column(name = "reason") String reason;
        @Column(name = "decided_at") Instant decidedAt;
        @Column(name = "decided_by") String decidedBy;
    }
}
