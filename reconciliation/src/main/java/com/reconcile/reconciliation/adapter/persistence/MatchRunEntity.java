package com.reconcile.reconciliation.adapter.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_run")
class MatchRunEntity {

    @Id
    UUID id;

    @Column(name = "status")
    String status;

    @Column(name = "started_at")
    Instant startedAt;

    @Column(name = "completed_at")
    Instant completedAt;

    @Column(name = "matched_count")
    int matchedCount;

    @Column(name = "unmatched_count")
    int unmatchedCount;
}
