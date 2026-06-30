package com.reconcile.reconciliation.adapter.persistence;

import com.reconcile.reconciliation.domain.ToleranceConfig;
import com.reconcile.reconciliation.domain.ToleranceConfigRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JpaToleranceConfigRepository implements ToleranceConfigRepository {

    private final ToleranceConfigJpaRepository jpa;

    public JpaToleranceConfigRepository(ToleranceConfigJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ToleranceConfig findCurrent() {
        return jpa.findById(Boolean.TRUE).map(ToleranceConfigEntity::toDomain).orElseGet(ToleranceConfig::defaults);
    }

    @Override
    public void save(ToleranceConfig config) {
        jpa.save(ToleranceConfigEntity.from(config));
    }
}
