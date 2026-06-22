package com.reconcile.tenant.domain;

import com.reconcile.shared.domain.TenantId;
import java.util.List;
import java.util.Optional;

public interface TenantRepository {

    void save(Tenant tenant);

    Optional<Tenant> findById(TenantId id);

    Optional<Tenant> findBySlug(String slug);

    List<Tenant> findAllActive();
}
