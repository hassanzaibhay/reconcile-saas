package com.reconcile.reconciliation.application;

import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.reconciliation.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultMatchingEngine implements MatchingEngine {

    private final AuditDecisionRepository auditDecisionRepository;
    private final ApplicationEventPublisher events;

    public DefaultMatchingEngine(
            AuditDecisionRepository auditDecisionRepository,
            ApplicationEventPublisher events) {
        this.auditDecisionRepository = auditDecisionRepository;
        this.events = events;
    }

    @Override
    @Transactional
    public MatchRunResult run(
            MatchRunId runId, List<MatchRule> rules, Iterable<LedgerEntry> entries) {
        List<LedgerEntry> pool = new ArrayList<>();
        entries.forEach(pool::add);

        Set<LedgerEntryId> matched = new LinkedHashSet<>();
        Map<LedgerEntryId, LedgerEntryId> matchPairs = new LinkedHashMap<>();
        List<Discrepancy> discrepancies = new ArrayList<>();

        for (LedgerEntry candidate : pool) {
            if (matched.contains(candidate.id())) continue;

            boolean found = false;
            for (MatchRule rule : rules) {
                Optional<LedgerEntry> counterpart =
                        rule.match(
                                candidate,
                                pool.stream()
                                        .filter(e -> !matched.contains(e.id()))
                                        .toList());
                if (counterpart.isPresent()) {
                    LedgerEntry other = counterpart.get();
                    matched.add(candidate.id());
                    matched.add(other.id());
                    matchPairs.put(candidate.id(), other.id());
                    recordDecision(runId, rule.ruleId(), candidate.id(), "MATCHED", null);
                    recordDecision(runId, rule.ruleId(), other.id(), "MATCHED", null);
                    found = true;
                    break;
                }
            }

            if (!found) {
                Discrepancy d =
                        Discrepancy.of(runId, candidate.id(), Discrepancy.Reason.NO_COUNTERPART);
                discrepancies.add(d);
                recordDecision(
                        runId,
                        "NONE",
                        candidate.id(),
                        "UNMATCHED",
                        Discrepancy.Reason.NO_COUNTERPART.name());
            }
        }

        MatchRunResult result = new MatchRunResult(runId, matchPairs, discrepancies);
        events.publishEvent(new MatchRunCompletedEvent(runId));
        return result;
    }

    private void recordDecision(
            MatchRunId runId, String ruleId, LedgerEntryId entryId, String decision,
            String reason) {
        auditDecisionRepository.save(
                new AuditDecision(
                        UUID.randomUUID(),
                        runId,
                        ruleId,
                        entryId,
                        decision,
                        reason,
                        Instant.now(),
                        "SYSTEM"));
    }
}
