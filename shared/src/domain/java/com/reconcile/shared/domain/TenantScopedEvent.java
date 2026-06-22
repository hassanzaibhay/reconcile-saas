package com.reconcile.shared.domain;

/**
 * Base type for all domain events that cross module boundaries. Every {@code
 * @ApplicationModuleListener} must consume a subtype of this class so that the
 * {@code TenantAwareEventListenerAdvice} can propagate {@link TenantContext} from the payload
 * before dispatching.
 */
public abstract class TenantScopedEvent {

    private final TenantId tenantId;

    protected TenantScopedEvent(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId must not be null");
        this.tenantId = tenantId;
    }

    public TenantId tenantId() {
        return tenantId;
    }
}
