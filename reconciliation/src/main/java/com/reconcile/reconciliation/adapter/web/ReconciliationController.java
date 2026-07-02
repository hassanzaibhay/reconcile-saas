package com.reconcile.reconciliation.adapter.web;

import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.ledger.domain.LedgerEntryRepository;
import com.reconcile.reconciliation.application.ReconciliationOrchestrator;
import com.reconcile.reconciliation.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reconciliation")
class ReconciliationController {

    private final ReconciliationOrchestrator orchestrator;
    private final LedgerEntryRepository ledgerEntryRepository;

    ReconciliationController(ReconciliationOrchestrator orchestrator, LedgerEntryRepository ledgerEntryRepository) {
        this.orchestrator = orchestrator;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a reconciliation run against all ledger entries")
    RunResponse startRun() {
        MatchRunId runId = MatchRunId.generate();
        List<LedgerEntry> entries = ledgerEntryRepository.findAll();
        MatchRunResult result = orchestrator.orchestrate(runId, List.of(new ExactAmountAndDateRule()), entries);
        return new RunResponse(runId.toString(), result.matchedCount(), result.unmatchedCount());
    }

    record RunResponse(String runId, int matched, int unmatched) {}
}
