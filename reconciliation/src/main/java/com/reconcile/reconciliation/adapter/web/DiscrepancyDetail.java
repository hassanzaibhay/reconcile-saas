package com.reconcile.reconciliation.adapter.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Detail view. {@code unmatchedEntryId}/{@code amount}/{@code currency} are null for AMBIGUOUS rows;
 * {@code clusterMembers} is empty for UNMATCHED rows.
 */
record DiscrepancyDetail(
        UUID id,
        UUID runId,
        String type,
        String status,
        UUID unmatchedEntryId,
        BigDecimal amount,
        String currency,
        List<UUID> clusterMembers) {}
