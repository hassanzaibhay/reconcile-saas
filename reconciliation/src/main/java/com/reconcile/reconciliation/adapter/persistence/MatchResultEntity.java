package com.reconcile.reconciliation.adapter.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_result")
class MatchResultEntity {

    @Id
    UUID id;

    @Column(name = "match_run_id")
    UUID matchRunId;

    @Column(name = "left_entry_id")
    UUID leftEntryId;

    @Column(name = "right_entry_id")
    UUID rightEntryId;

    @Column(name = "rule_id")
    String ruleId;

    @Column(name = "matched_at")
    Instant matchedAt;
}
