package com.reconcile.app.toleranceconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.reconcile.reconciliation.domain.MatchingAxis;
import com.reconcile.reconciliation.domain.ToleranceConfig;
import com.reconcile.reconciliation.domain.ToleranceConfigRepository;
import com.reconcile.shared.domain.TenantContext;
import com.reconcile.tenant.application.TenantProvisioningService;
import com.reconcile.tenant.domain.Tenant;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
@DisplayName("ToleranceConfig — tenant isolation")
class ToleranceConfigTenantIsolationTest {

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
    TenantProvisioningService tenantProvisioningService;

    @Autowired
    ToleranceConfigRepository repository;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("T2 findCurrent() returns seeded defaults when T1 has custom axis=DIFFERENCE")
    void configChangesInT1DoNotLeakToT2() {
        Tenant t1 = tenantProvisioningService.provision("cfg-iso-t1-" + shortId());
        Tenant t2 = tenantProvisioningService.provision("cfg-iso-t2-" + shortId());

        TenantContext.set(t1.id());
        try {
            repository.save(new ToleranceConfig(
                    UUID.randomUUID(), new BigDecimal("0.99"), new BigDecimal("0.01"), 7, MatchingAxis.DIFFERENCE));
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(t2.id());
        try {
            ToleranceConfig t2Config = repository.findCurrent();
            assertThat(t2Config.absoluteTolerance())
                    .as("T2 must see its own seeded 0.01, not T1's 0.99")
                    .isEqualByComparingTo(new BigDecimal("0.01"));
            assertThat(t2Config.axis())
                    .as("T2 must see seeded SUM_TO_ZERO, not T1's DIFFERENCE")
                    .isEqualTo(MatchingAxis.SUM_TO_ZERO);
        } finally {
            TenantContext.clear();
        }
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
