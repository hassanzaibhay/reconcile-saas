package com.reconcile.reconciliation.application;

import com.reconcile.reconciliation.domain.Discrepancy;

public interface DiscrepancyRepository {
    void save(Discrepancy discrepancy);
}
