package com.reconcile.ledger.adapter.persistence;

import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.ledger.domain.LedgerEntryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaLedgerEntryRepository implements LedgerEntryRepository {

    private final LedgerEntryJpaRepository jpa;

    public JpaLedgerEntryRepository(LedgerEntryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(LedgerEntry entry) {
        jpa.save(LedgerEntryEntity.from(entry));
    }

    @Override
    public void saveAll(List<LedgerEntry> entries) {
        jpa.saveAll(entries.stream().map(LedgerEntryEntity::from).toList());
    }

    @Override
    public Optional<LedgerEntry> findById(LedgerEntryId id) {
        return jpa.findById(id.value()).map(LedgerEntryEntity::toDomain);
    }

    @Override
    public List<LedgerEntry> findAll() {
        return jpa.findAll().stream().map(LedgerEntryEntity::toDomain).toList();
    }

    @Override
    public List<LedgerEntry> findByFeedIdAndDateRange(
            String feedId, LocalDate from, LocalDate to) {
        return jpa.findByFeedIdAndDateRange(feedId, from, to).stream()
                .map(LedgerEntryEntity::toDomain)
                .toList();
    }
}
