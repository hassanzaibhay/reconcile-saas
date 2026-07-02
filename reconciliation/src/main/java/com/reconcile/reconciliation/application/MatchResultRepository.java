package com.reconcile.reconciliation.application;

import com.reconcile.reconciliation.domain.MatchPair;
import com.reconcile.reconciliation.domain.MatchRunId;
import java.util.List;

public interface MatchResultRepository {
    void saveAll(MatchRunId runId, List<MatchPair> pairs);
}
