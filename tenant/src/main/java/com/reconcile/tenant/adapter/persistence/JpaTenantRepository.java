package com.reconcile.tenant.adapter.persistence;

import com.reconcile.shared.domain.TenantId;
import com.reconcile.tenant.domain.Tenant;
import com.reconcile.tenant.domain.TenantRepository;
import com.reconcile.tenant.domain.TenantStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaTenantRepository implements TenantRepository {

    private final TenantJpaRepository jpa;

    JpaTenantRepository(TenantJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Tenant tenant) {
        jpa.save(TenantEntity.from(tenant));
    }

    @Override
    public Optional<Tenant> findById(TenantId id) {
        return jpa.findById(id.value()).map(TenantEntity::toDomain);
    }

    @Override
    public Optional<Tenant> findBySlug(String slug) {
        return jpa.findBySlug(slug).map(TenantEntity::toDomain);
    }

    @Override
    public List<Tenant> findAllActive() {
        return jpa.findAllByStatus(TenantStatus.ACTIVE).stream()
                .map(TenantEntity::toDomain)
                .toList();
    }
}
