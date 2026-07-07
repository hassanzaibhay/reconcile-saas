package com.reconcile.reconciliation.adapter.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matched_entry")
class MatchedEntryEntity {

    @Id
    @Column(name = "ledger_entry_id")
    UUID ledgerEntryId;

    @Column(name = "match_result_id")
    UUID matchResultId;

    @Column(name = "matched_at")
    Instant matchedAt;
}
