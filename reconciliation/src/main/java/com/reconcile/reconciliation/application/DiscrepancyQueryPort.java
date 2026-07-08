package com.reconcile.reconciliation.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscrepancyQueryPort {

    /** Fetches up to {@code query.limitPlusOne()} rows ordered {@code created_at DESC, id DESC}. */
    List<DiscrepancyListRow> list(DiscrepancyListQuery query);

    Optional<DiscrepancyForResolution> loadDetail(UUID discrepancyId);

    /**
     * Monetary context for a single ledger entry, for enriching the {@code DiscrepancyDetail} web view.
     * Deliberately separate from {@link #loadDetail}: {@link DiscrepancyForResolution} is shared with the
     * resolution command path and its shape must not change, so amount/currency are fetched independently
     * rather than added to that record.
     */
    Optional<EntryMoney> findEntryMoney(UUID ledgerEntryId);
}
