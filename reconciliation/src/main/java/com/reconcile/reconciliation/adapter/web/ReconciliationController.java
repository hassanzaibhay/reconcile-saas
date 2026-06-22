package com.reconcile.reconciliation.adapter.web;

import com.reconcile.ledger.domain.LedgerEntryRepository;
import com.reconcile.reconciliation.domain.*;
import com.reconcile.reconciliation.application.DefaultMatchingEngine;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reconciliation")
class ReconciliationController {

    private final DefaultMatchingEngine matchingEngine;
    private final LedgerEntryRepository ledgerEntryRepository;

    ReconciliationController(
            DefaultMatchingEngine matchingEngine,
            LedgerEntryRepository ledgerEntryRepository) {
        this.matchingEngine = matchingEngine;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a reconciliation run against all ledger entries")
    RunResponse startRun() {
        MatchRunId runId = MatchRunId.generate();
        MatchRunResult result =
                matchingEngine.run(
                        runId,
                        List.of(new ExactAmountAndDateRule()),
                        ledgerEntryRepository.findAll());
        return new RunResponse(
                runId.toString(), result.matchedCount(), result.unmatchedCount());
    }

    record RunResponse(String runId, int matched, int unmatched) {}
}
