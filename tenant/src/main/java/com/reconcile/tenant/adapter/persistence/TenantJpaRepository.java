package com.reconcile.tenant.adapter.persistence;

import com.reconcile.tenant.domain.TenantStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TenantJpaRepository extends JpaRepository<TenantEntity, UUID> {

    Optional<TenantEntity> findBySlug(String slug);

    List<TenantEntity> findAllByStatus(TenantStatus status);
}
