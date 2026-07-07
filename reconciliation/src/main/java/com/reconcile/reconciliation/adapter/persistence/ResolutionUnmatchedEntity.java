package com.reconcile.reconciliation.adapter.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "resolution_unmatched")
@IdClass(ResolutionUnmatchedEntity.ResolutionUnmatchedId.class)
class ResolutionUnmatchedEntity {

    @Id
    @Column(name = "resolution_id")
    UUID resolutionId;

    @Id
    @Column(name = "ledger_entry_id")
    UUID ledgerEntryId;

    static class ResolutionUnmatchedId implements Serializable {
        UUID resolutionId;
        UUID ledgerEntryId;

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ResolutionUnmatchedId r)) return false;
            return Objects.equals(resolutionId, r.resolutionId) && Objects.equals(ledgerEntryId, r.ledgerEntryId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resolutionId, ledgerEntryId);
        }
    }
}
