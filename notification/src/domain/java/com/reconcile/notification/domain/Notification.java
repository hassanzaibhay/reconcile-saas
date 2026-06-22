package com.reconcile.notification.domain;

import com.reconcile.shared.domain.TenantId;
import java.time.Instant;
import java.util.UUID;

public record Notification(
        UUID id, TenantId tenantId, String type, String payload, Instant createdAt) {

    public static Notification of(TenantId tenantId, String type, String payload) {
        return new Notification(UUID.randomUUID(), tenantId, type, payload, Instant.now());
    }
}
