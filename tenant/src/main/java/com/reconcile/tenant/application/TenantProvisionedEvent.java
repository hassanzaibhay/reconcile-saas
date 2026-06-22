package com.reconcile.tenant.application;

import com.reconcile.shared.domain.TenantId;
import com.reconcile.shared.domain.TenantScopedEvent;

public class TenantProvisionedEvent extends TenantScopedEvent {

    public TenantProvisionedEvent(TenantId tenantId) {
        super(tenantId);
    }
}
