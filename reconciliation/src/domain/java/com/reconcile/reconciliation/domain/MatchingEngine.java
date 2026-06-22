package com.reconcile.reconciliation.domain;

import com.reconcile.ledger.domain.LedgerEntry;
import java.util.List;

/** Deterministic matching engine. Same inputs + same rules → same result + same audit trail. */
public interface MatchingEngine {

    MatchRunResult run(MatchRunId runId, List<MatchRule> rules, Iterable<LedgerEntry> entries);
}
