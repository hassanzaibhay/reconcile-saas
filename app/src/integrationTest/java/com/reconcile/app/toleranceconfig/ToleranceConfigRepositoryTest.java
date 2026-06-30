package com.reconcile.app.toleranceconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.reconcile.app.support.TenantTest;
import com.reconcile.reconciliation.domain.MatchingAxis;
import com.reconcile.reconciliation.domain.ToleranceConfig;
import com.reconcile.reconciliation.domain.ToleranceConfigRepository;
import java.math.BigDecimal;
import java.util.UUID;
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
@TenantTest
@DisplayName("ToleranceConfigRepository — CRUD")
class ToleranceConfigRepositoryTest {

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
    ToleranceConfigRepository repository;

    @Test
    @DisplayName("findCurrent() returns seeded defaults: 0.01 / 0.001 / 0 / SUM_TO_ZERO")
    void findCurrentReturnsSeededDefaults() {
        ToleranceConfig cfg = repository.findCurrent();

        assertThat(cfg.absoluteTolerance()).isEqualByComparingTo(new BigDecimal("0.01"));
        assertThat(cfg.percentageTolerance()).isEqualByComparingTo(new BigDecimal("0.001"));
        assertThat(cfg.maxDateDriftDays()).isEqualTo(0);
        assertThat(cfg.axis()).isEqualTo(MatchingAxis.SUM_TO_ZERO);
    }

    @Test
    @DisplayName("save() updates all fields; findCurrent() returns updated values")
    void saveAndReload() {
        ToleranceConfig updated = new ToleranceConfig(
                UUID.randomUUID(), new BigDecimal("0.25"), new BigDecimal("0.005"), 3, MatchingAxis.DIFFERENCE);

        repository.save(updated);
        ToleranceConfig reloaded = repository.findCurrent();

        assertThat(reloaded.absoluteTolerance()).isEqualByComparingTo(new BigDecimal("0.25"));
        assertThat(reloaded.percentageTolerance()).isEqualByComparingTo(new BigDecimal("0.005"));
        assertThat(reloaded.maxDateDriftDays()).isEqualTo(3);
        assertThat(reloaded.axis()).isEqualTo(MatchingAxis.DIFFERENCE);
    }
}
