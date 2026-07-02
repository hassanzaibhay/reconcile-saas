package com.reconcile.reconciliation.domain;

import com.reconcile.ledger.domain.LedgerEntryId;
import java.util.UUID;

public sealed interface Discrepancy permits Discrepancy.Unmatched, Discrepancy.Ambiguous {

    UUID id();

    MatchRunId matchRunId();

    static Unmatched of(MatchRunId matchRunId, LedgerEntryId entryId) {
        return new Unmatched(UUID.randomUUID(), matchRunId, entryId);
    }

    record Unmatched(UUID id, MatchRunId matchRunId, LedgerEntryId entryId) implements Discrepancy {}

    record Ambiguous(UUID id, MatchRunId matchRunId, AmbiguousCluster cluster) implements Discrepancy {}
}
