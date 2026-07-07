package com.reconcile.reconciliation.adapter.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discrepancy")
class DiscrepancyEntity {

    @Id
    UUID id;

    @Column(name = "match_run_id")
    UUID matchRunId;

    @Column(name = "type")
    String type;

    @Column(name = "unmatched_entry_id")
    UUID unmatchedEntryId;

    @Column(name = "created_at")
    Instant createdAt;

    @Column(name = "status")
    String status = "OPEN";

    @Version
    @Column(name = "version")
    int version;
}
