package com.reconcile.reconciliation.adapter.persistence;

import com.reconcile.reconciliation.domain.MatchingAxis;
import com.reconcile.reconciliation.domain.ToleranceConfig;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "reconciliation_config")
class ToleranceConfigEntity {

    @Id
    @Column(name = "singleton")
    private Boolean singleton = Boolean.TRUE;

    @Column(name = "absolute_tolerance", nullable = false, precision = 19, scale = 4)
    private BigDecimal absoluteTolerance;

    @Column(name = "percentage_tolerance", nullable = false, precision = 8, scale = 6)
    private BigDecimal percentageTolerance;

    @Column(name = "max_date_drift_days", nullable = false)
    private int maxDateDriftDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "axis", nullable = false, length = 20)
    private MatchingAxis axis;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ToleranceConfigEntity() {}

    static ToleranceConfigEntity from(ToleranceConfig config) {
        ToleranceConfigEntity e = new ToleranceConfigEntity();
        e.singleton = Boolean.TRUE;
        e.absoluteTolerance = config.absoluteTolerance();
        e.percentageTolerance = config.percentageTolerance();
        e.maxDateDriftDays = config.maxDateDriftDays();
        e.axis = config.axis();
        e.updatedAt = Instant.now();
        return e;
    }

    ToleranceConfig toDomain() {
        return new ToleranceConfig(null, absoluteTolerance, percentageTolerance, maxDateDriftDays, axis);
    }
}
