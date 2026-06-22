package com.reconcile.ledger.adapter.persistence;

import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.shared.domain.Money;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "ledger_entry")
class LedgerEntryEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "feed_id", nullable = false)
    private String feedId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description")
    private String description;

    @Column(name = "reference")
    private String reference;

    @Column(name = "ingestion_run_id", columnDefinition = "uuid")
    private UUID ingestionRunId;

    protected LedgerEntryEntity() {}

    static LedgerEntryEntity from(LedgerEntry entry) {
        LedgerEntryEntity e = new LedgerEntryEntity();
        e.id = entry.id().value();
        e.feedId = entry.feedId();
        e.entryDate = entry.entryDate();
        e.amount = entry.amount().amount();
        e.currency = entry.amount().currency().getCurrencyCode();
        e.description = entry.description();
        e.reference = entry.reference();
        e.ingestionRunId = entry.ingestionRunId();
        return e;
    }

    LedgerEntry toDomain() {
        return new LedgerEntry(
                LedgerEntryId.of(id),
                feedId,
                entryDate,
                Money.of(amount, Currency.getInstance(currency)),
                description,
                reference,
                ingestionRunId);
    }
}
