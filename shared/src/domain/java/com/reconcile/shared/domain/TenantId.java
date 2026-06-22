package com.reconcile.shared.domain;

import java.util.UUID;

public record TenantId(UUID value) {

    public TenantId {
        if (value == null) throw new IllegalArgumentException("TenantId value must not be null");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public static TenantId of(String uuid) {
        return new TenantId(UUID.fromString(uuid));
    }

    /** PostgreSQL schema name: "tenant_" + 32 hex chars (39 chars total, within 63-char limit). */
    public String schemaName() {
        return "tenant_" + value.toString().replace("-", "");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
