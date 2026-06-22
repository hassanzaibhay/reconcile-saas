package com.reconcile.reconciliation.domain;

import com.reconcile.ledger.domain.LedgerEntryId;
import java.util.UUID;

public record Discrepancy(UUID id, MatchRunId matchRunId, LedgerEntryId entryId, Reason reason) {

    public enum Reason {
        NO_COUNTERPART,
        AMOUNT_MISMATCH,
        DATE_MISMATCH,
    }

    public static Discrepancy of(MatchRunId matchRunId, LedgerEntryId entryId, Reason reason) {
        return new Discrepancy(UUID.randomUUID(), matchRunId, entryId, reason);
    }
}
