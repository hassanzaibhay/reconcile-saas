package com.reconcile.ledger.domain;

import java.util.UUID;

public record LedgerEntryId(UUID value) {

    public static LedgerEntryId generate() {
        return new LedgerEntryId(UUID.randomUUID());
    }

    public static LedgerEntryId of(UUID value) {
        return new LedgerEntryId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
