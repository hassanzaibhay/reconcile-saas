package com.reconcile.app.toleranceconfig;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reconcile.app.support.TenantTest;
import com.reconcile.shared.domain.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TenantTest
@DisplayName("ToleranceConfig — DB-enforced singleton")
class ToleranceConfigSingletonEnforcedTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Second INSERT into reconciliation_config is rejected by PRIMARY KEY constraint")
    void secondInsertRejectedByDb(TenantId tenantId) {
        String schema = tenantId.schemaName();
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO "
                        + schema
                        + ".reconciliation_config"
                        + " (singleton, absolute_tolerance, percentage_tolerance,"
                        + "  max_date_drift_days, axis, updated_at)"
                        + " VALUES (TRUE, 0.05, 0.001, 0, 'SUM_TO_ZERO', now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
