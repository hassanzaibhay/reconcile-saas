package com.reconcile.app.support;

import com.reconcile.shared.domain.TenantContext;
import com.reconcile.shared.domain.TenantId;
import java.lang.annotation.*;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.extension.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * JUnit 5 extension + annotation that provisions a UUID-named tenant schema in the Testcontainers
 * Postgres, sets {@link TenantContext} before each test, and drops the schema + clears the context
 * on teardown.
 *
 * <p>Usage: annotate the test class or method with {@code @TenantTest}.
 */
public class TenantTestExtension
        implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final String TENANT_ID_KEY = "tenantId";

    @Override
    public void beforeEach(ExtensionContext context) {
        TenantId id = TenantId.of(UUID.randomUUID());
        context.getStore(ExtensionContext.Namespace.create(TenantTestExtension.class))
                .put(TENANT_ID_KEY, id);

        DataSource ds = SpringExtension.getApplicationContext(context).getBean(DataSource.class);
        String schema = id.schemaName();
        Flyway.configure()
                .dataSource(ds)
                .schemas(schema)
                .locations("classpath:db/migration/tenant")
                .createSchemas(true)
                .load()
                .migrate();

        TenantContext.set(id);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TenantId id =
                (TenantId)
                        context.getStore(
                                        ExtensionContext.Namespace.create(
                                                TenantTestExtension.class))
                                .get(TENANT_ID_KEY);
        try {
            if (id != null) {
                DataSource ds =
                        SpringExtension.getApplicationContext(context).getBean(DataSource.class);
                ds.getConnection()
                        .createStatement()
                        .execute("DROP SCHEMA IF EXISTS " + id.schemaName() + " CASCADE");
            }
        } catch (Exception e) {
            // best-effort teardown
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    public boolean supportsParameter(
            ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType().equals(TenantId.class);
    }

    @Override
    public Object resolveParameter(
            ParameterContext parameterContext, ExtensionContext extensionContext) {
        return extensionContext
                .getStore(ExtensionContext.Namespace.create(TenantTestExtension.class))
                .get(TENANT_ID_KEY, TenantId.class);
    }
}
