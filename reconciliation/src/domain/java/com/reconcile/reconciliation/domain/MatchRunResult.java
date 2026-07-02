package com.reconcile.reconciliation.domain;

import java.util.List;

public record MatchRunResult(
        MatchRunId runId,
        List<MatchPair> matches,
        List<AmbiguousCluster> ambiguousClusters,
        List<Discrepancy.Unmatched> discrepancies) {

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
