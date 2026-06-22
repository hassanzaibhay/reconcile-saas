package com.reconcile.ingestion.application.batch;

import com.reconcile.ingestion.domain.LedgerEntryDraft;
import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.ledger.domain.LedgerEntryRepository;
import java.util.UUID;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@StepScope
class LedgerEntryWriter implements ItemWriter<LedgerEntryDraft> {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final UUID ingestionRunId;

    LedgerEntryWriter(
            LedgerEntryRepository ledgerEntryRepository,
            @Value("#{jobParameters['ingestionRunId']}") String ingestionRunId) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ingestionRunId = UUID.fromString(ingestionRunId);
    }

    @Override
    public void write(Chunk<? extends LedgerEntryDraft> chunk) {
        ledgerEntryRepository.saveAll(chunk.getItems().stream()
                .map(draft -> LedgerEntry.create(
                        draft.feedId(),
                        draft.entryDate(),
                        draft.amount(),
                        draft.description(),
                        draft.reference(),
                        ingestionRunId))
                .toList());
    }
}
