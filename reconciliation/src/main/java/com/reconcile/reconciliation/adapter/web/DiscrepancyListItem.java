package com.reconcile.reconciliation.adapter.web;

import com.reconcile.reconciliation.application.DiscrepancyListRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Triage-row view. {@code amount}/{@code currency} are null for AMBIGUOUS discrepancies. */
record DiscrepancyListItem(
        UUID id, String type, String status, UUID runId, Instant createdAt, BigDecimal amount, String currency) {

    static DiscrepancyListItem from(DiscrepancyListRow row) {
        return new DiscrepancyListItem(
                row.id(), row.type(), row.status(), row.matchRunId(), row.createdAt(), row.amount(), row.currency());
    }
}
