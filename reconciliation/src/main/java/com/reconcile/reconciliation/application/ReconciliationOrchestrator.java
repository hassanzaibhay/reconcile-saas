package com.reconcile.reconciliation.application;

import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.reconciliation.domain.*;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wraps engine.run() and all persistence writes in a single transaction so a partial failure
 * cannot leave audit rows without their corresponding match_result / discrepancy rows.
 */
@Service
public class ReconciliationOrchestrator {

    private final MatchingEngine engine;
    private final MatchRunRegistry matchRunRegistry;
    private final MatchResultRepository matchResultRepository;
    private final DiscrepancyRepository discrepancyRepository;

    public ReconciliationOrchestrator(
            MatchingEngine engine,
            MatchRunRegistry matchRunRegistry,
            MatchResultRepository matchResultRepository,
            DiscrepancyRepository discrepancyRepository) {
        this.engine = engine;
        this.matchRunRegistry = matchRunRegistry;
        this.matchResultRepository = matchResultRepository;
        this.discrepancyRepository = discrepancyRepository;
    }

    @Transactional
    public MatchRunResult orchestrate(MatchRunId runId, List<MatchRule> rules, Iterable<LedgerEntry> entries) {
        matchRunRegistry.create(runId);
        MatchRunResult result = engine.run(runId, rules, entries);

        matchResultRepository.saveAll(runId, result.matches());

        result.discrepancies().forEach(discrepancyRepository::save);

        for (AmbiguousCluster cluster : result.ambiguousClusters()) {
            discrepancyRepository.save(new Discrepancy.Ambiguous(UUID.randomUUID(), runId, cluster));
        }

        matchRunRegistry.complete(runId, result.matchedCount(), result.unmatchedCount());
        return result;
    }
}
