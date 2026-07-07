package com.reconcile.reconciliation.adapter.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "resolution_pairing")
@IdClass(ResolutionPairingEntity.PairingId.class)
class ResolutionPairingEntity {

    @Id
    @Column(name = "resolution_id")
    UUID resolutionId;

    @Id
    @Column(name = "left_entry_id")
    UUID leftEntryId;

    @Id
    @Column(name = "right_entry_id")
    UUID rightEntryId;

    static class PairingId implements Serializable {
        UUID resolutionId;
        UUID leftEntryId;
        UUID rightEntryId;

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PairingId p)) return false;
            return Objects.equals(resolutionId, p.resolutionId)
                    && Objects.equals(leftEntryId, p.leftEntryId)
                    && Objects.equals(rightEntryId, p.rightEntryId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resolutionId, leftEntryId, rightEntryId);
        }
    }
}
