package com.reconcile.reconciliation.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Triage-row read model. {@code amount}/{@code currency} come from the joined {@code ledger_entry} and
 * are null for AMBIGUOUS discrepancies (no single entry).
 */
public record DiscrepancyListRow(
        UUID id, UUID matchRunId, String type, String status, Instant createdAt, BigDecimal amount, String currency) {}
