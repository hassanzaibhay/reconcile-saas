package com.reconcile.reconciliation.domain;

import com.reconcile.ledger.domain.LedgerEntryId;

/**
 * A matched pair produced by the engine, carrying the left entry, right entry, and the rule that
 * formed the component. Authoritative provenance — sourced at result-assembly time, not reconstructed.
 */
public record MatchPair(LedgerEntryId left, LedgerEntryId right, String ruleId) {}
