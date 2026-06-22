package com.reconcile.tenant.domain;

import com.reconcile.shared.domain.TenantId;
import java.time.Instant;

public record Tenant(TenantId id, String slug, TenantStatus status, Instant createdAt) {

    public static Tenant create(TenantId id, String slug) {
        return new Tenant(id, slug, TenantStatus.ACTIVE, Instant.now());
    }

    public String schemaName() {
        return id.schemaName();
    }
}
