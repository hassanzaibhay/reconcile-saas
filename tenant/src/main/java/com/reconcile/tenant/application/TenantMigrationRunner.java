package com.reconcile.tenant.application;

import com.reconcile.tenant.domain.Tenant;
import com.reconcile.tenant.domain.TenantRepository;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Applies per-tenant migrations to every active tenant schema at boot.
 *
 * <p>Single-replica assumption: concurrent boots may race on Flyway's schema lock. Above ~50
 * tenants, decouple to a Redis-leased migration job. See multitenancy.md.
 */
@Component
@Order(1)
public class TenantMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationRunner.class);

    private final TenantRepository tenantRepository;
    private final DataSource dataSource;

    public TenantMigrationRunner(TenantRepository tenantRepository, DataSource dataSource) {
        this.tenantRepository = tenantRepository;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Tenant tenant : tenantRepository.findAllActive()) {
            try {
                log.info("Migrating schema for tenant: {}", tenant.id());
                Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(tenant.schemaName())
                        .locations("classpath:db/migration/tenant")
                        .createSchemas(true)
                        .load()
                        .migrate();
            } catch (Exception ex) {
                log.error("Migration failed for tenant: {}. Halting boot.", tenant.id(), ex);
                throw new IllegalStateException(
                        "Tenant migration failed for " + tenant.id() + ". Forward-only migrations"
                                + " require all tenant schemas to be current before serving"
                                + " traffic.",
                        ex);
            }
        }
    }
}
