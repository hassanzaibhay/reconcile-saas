package com.reconcile.tenant.adapter.persistence;

import com.reconcile.shared.domain.TenantId;
import com.reconcile.tenant.domain.Tenant;
import com.reconcile.tenant.domain.TenantStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "public", name = "tenants")
class TenantEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true, length = 63)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenantStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TenantEntity() {}

    static TenantEntity from(Tenant tenant) {
        TenantEntity e = new TenantEntity();
        e.id = tenant.id().value();
        e.slug = tenant.slug();
        e.status = tenant.status();
        e.createdAt = tenant.createdAt();
        return e;
    }

    Tenant toDomain() {
        return new Tenant(TenantId.of(id), slug, status, createdAt);
    }
}
