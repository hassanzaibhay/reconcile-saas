package com.reconcile.ledger.domain;

import com.reconcile.shared.domain.Money;
import java.time.LocalDate;
import java.util.UUID;

public record LedgerEntry(
        LedgerEntryId id,
        String feedId,
        LocalDate entryDate,
        Money amount,
        String description,
        String reference,
        UUID ingestionRunId) {

    public static LedgerEntry create(
            String feedId,
            LocalDate entryDate,
            Money amount,
            String description,
            String reference,
            UUID ingestionRunId) {
        return new LedgerEntry(
                LedgerEntryId.generate(), feedId, entryDate, amount, description, reference,
                ingestionRunId);
    }
}
