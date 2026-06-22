package com.reconcile.reporting.adapter.web;

import com.reconcile.reporting.domain.ReconciliationReport;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
class ReportingController {

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/runs/{runId}")
    @Operation(summary = "Get reconciliation report for a run")
    ReconciliationReport getReport(@PathVariable UUID runId) {
        long matched = (long) em.createNativeQuery("SELECT COUNT(*) FROM match_result WHERE match_run_id = ?1")
                .setParameter(1, runId)
                .getSingleResult();
        @SuppressWarnings("unchecked")
        List<Object[]> discRows = em.createNativeQuery(
                        "SELECT d.entry_id, d.reason FROM discrepancy d WHERE d.match_run_id = ?1")
                .setParameter(1, runId)
                .getResultList();

        List<ReconciliationReport.DiscrepancySummary> summaries = discRows.stream()
                .map(row -> new ReconciliationReport.DiscrepancySummary((UUID) row[0], (String) row[1]))
                .toList();

        return new ReconciliationReport(runId, Instant.now(), (int) matched, summaries.size(), summaries);
    }
}
