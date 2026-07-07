package com.reconcile.reconciliation.adapter.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resolution")
class ResolutionEntity {

    @Id
    UUID id;

    @Column(name = "discrepancy_id")
    UUID discrepancyId;

    @Column(name = "kind")
    String kind;

    @Column(name = "resolved_by")
    String resolvedBy;

    @Column(name = "resolved_at")
    Instant resolvedAt;
}
