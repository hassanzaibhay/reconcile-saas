package com.reconcile.reconciliation.domain;

import com.reconcile.ledger.domain.LedgerEntryId;
import java.util.List;
import java.util.Map;

public record MatchRunResult(
        MatchRunId runId,
        Map<LedgerEntryId, LedgerEntryId> matches,
        List<AmbiguousCluster> ambiguousClusters,
        List<Discrepancy> discrepancies) {

    public int matchedCount() {
        return matches.size();
    }

    public int ambiguousCount() {
        return ambiguousClusters.size();
    }

    public int unmatchedCount() {
        return discrepancies.size();
    }
}
