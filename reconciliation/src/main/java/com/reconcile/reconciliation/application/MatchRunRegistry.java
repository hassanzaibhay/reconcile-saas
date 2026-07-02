package com.reconcile.reconciliation.application;

import com.reconcile.reconciliation.domain.MatchRunId;

public interface MatchRunRegistry {
    void create(MatchRunId runId);

    void complete(MatchRunId runId, int matchedCount, int unmatchedCount);
}
