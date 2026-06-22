package com.reconcile.ingestion.domain;

import com.reconcile.shared.domain.Money;
import java.time.LocalDate;

/** A parsed but not-yet-persisted ledger entry from a raw ingestion file. */
public record LedgerEntryDraft(
        String feedId, LocalDate entryDate, Money amount, String description, String reference) {}
