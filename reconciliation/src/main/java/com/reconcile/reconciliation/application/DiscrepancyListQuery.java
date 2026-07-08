package com.reconcile.reconciliation.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Keyset list query. {@code cursorCreatedAt}/{@code cursorId} are both null for the first page; both
 * non-null for a continuation. {@code status}/{@code type}/{@code runId} are null when the filter is not
 * active.
 */
public record DiscrepancyListQuery(
        String status, String type, UUID runId, Instant cursorCreatedAt, UUID cursorId, int limitPlusOne) {}
