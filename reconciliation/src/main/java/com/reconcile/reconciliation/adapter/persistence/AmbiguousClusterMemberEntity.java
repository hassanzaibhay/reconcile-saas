package com.reconcile.reconciliation.adapter.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ambiguous_cluster_member")
@IdClass(AmbiguousClusterMemberEntity.MemberId.class)
class AmbiguousClusterMemberEntity {

    @Id
    @Column(name = "discrepancy_id")
    UUID discrepancyId;

    @Id
    @Column(name = "ledger_entry_id")
    UUID ledgerEntryId;

    static class MemberId implements Serializable {
        UUID discrepancyId;
        UUID ledgerEntryId;

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MemberId m)) return false;
            return Objects.equals(discrepancyId, m.discrepancyId) && Objects.equals(ledgerEntryId, m.ledgerEntryId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(discrepancyId, ledgerEntryId);
        }
    }
}
