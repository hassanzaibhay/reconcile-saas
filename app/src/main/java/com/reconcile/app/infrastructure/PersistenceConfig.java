package com.reconcile.app.infrastructure;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration(proxyBeanMethods = false)
class PersistenceConfig {

    /**
     * Registers the connection provider and resolver beans with Hibernate's vendor properties.
     * @DependsOn("sharedFlyway") ensures the public schema is migrated before the EMF starts.
     */
    @Bean
    @DependsOn("sharedFlyway")
    HibernatePropertiesCustomizer hibernateMultiTenancyCustomizer(
            MultiTenantConnectionProvider<String> connectionProvider,
            CurrentTenantIdentifierResolver<String> tenantResolver) {
        return props -> {
            props.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        };
    }
}
