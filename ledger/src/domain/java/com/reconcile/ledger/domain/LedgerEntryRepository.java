package com.reconcile.ledger.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LedgerEntryRepository {

    void save(LedgerEntry entry);

    void saveAll(List<LedgerEntry> entries);

    Optional<LedgerEntry> findById(LedgerEntryId id);

    List<LedgerEntry> findAll();

    List<LedgerEntry> findByFeedIdAndDateRange(String feedId, LocalDate from, LocalDate to);
}
