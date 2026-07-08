package com.reconcile.shared.web.pagination;

import java.time.Instant;
import java.util.UUID;

/**
 * Decoded keyset position: the sort-key tuple {@code (sortKey, id)} plus the filter-set hash the cursor
 * was minted under. {@code sortKey} is always at microsecond precision (see {@link CursorCodec}) so it
 * compares identically to a Postgres {@code TIMESTAMPTZ} column and the keyset boundary never flip-flops.
 */
public record Cursor(Instant sortKey, UUID id, String filterHash) {}
