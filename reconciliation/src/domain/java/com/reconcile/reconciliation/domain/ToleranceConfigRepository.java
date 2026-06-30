package com.reconcile.reconciliation.domain;

public interface ToleranceConfigRepository {
    ToleranceConfig findCurrent();

    void save(ToleranceConfig config);
}
