package com.reconcile.tenant.application;

import com.reconcile.shared.domain.TenantId;
import com.reconcile.tenant.domain.Tenant;
import com.reconcile.tenant.domain.TenantRepository;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final DataSource dataSource;
    private final ApplicationEventPublisher events;

    public TenantProvisioningService(
            TenantRepository tenantRepository, DataSource dataSource, ApplicationEventPublisher events) {
        this.tenantRepository = tenantRepository;
        this.dataSource = dataSource;
        this.events = events;
    }

    @Transactional
    public Tenant provision(String slug) {
        TenantId id = TenantId.of(UUID.randomUUID());
        Tenant tenant = Tenant.create(id, slug);
        tenantRepository.save(tenant);
        migrateSchema(tenant.schemaName());
        events.publishEvent(new TenantProvisionedEvent(id));
        return tenant;
    }

    void migrateSchema(String schemaName) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations("classpath:db/migration/tenant")
                .createSchemas(true)
                .load()
                .migrate();
    }
}
