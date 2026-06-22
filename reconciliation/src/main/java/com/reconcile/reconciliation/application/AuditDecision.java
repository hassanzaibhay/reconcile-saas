package com.reconcile.reconciliation.application;

import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.reconciliation.domain.MatchRunId;
import java.time.Instant;
import java.util.UUID;

public record AuditDecision(
        UUID id,
        MatchRunId matchRunId,
        String ruleId,
        LedgerEntryId entryId,
        String decision,
        String reason,
        Instant decidedAt,
        String decidedBy) {}
