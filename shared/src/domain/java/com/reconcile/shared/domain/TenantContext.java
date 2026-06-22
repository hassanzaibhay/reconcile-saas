package com.reconcile.shared.domain;

/**
 * Thread-local tenant context. Fail-closed: {@link #current()} throws if unset — never defaults
 * to a tenant, never returns null.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantId> HOLDER = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId must not be null");
        HOLDER.set(tenantId);
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** @throws MissingTenantException if tenant context was not set on this thread */
    public static TenantId current() {
        TenantId id = HOLDER.get();
        if (id == null) {
            throw new MissingTenantException(
                    "No tenant context set on current thread. Every data-access path requires a"
                            + " tenant to be established via TenantContext.set() before use.");
        }
        return id;
    }

    public static boolean isSet() {
        return HOLDER.get() != null;
    }
}
