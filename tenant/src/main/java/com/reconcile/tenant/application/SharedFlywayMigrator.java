package com.reconcile.tenant.application;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs shared-schema (registry) migrations before the EntityManagerFactory is created.
 * The {@code sharedFlyway} bean is declared so callers can use {@code @DependsOn("sharedFlyway")}.
 */
@Configuration
public class SharedFlywayMigrator {

    @Bean
    public Flyway sharedFlyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .locations("classpath:db/migration/shared")
                .table("flyway_schema_history_shared")
                .load();
        flyway.migrate();
        return flyway;
    }
}
