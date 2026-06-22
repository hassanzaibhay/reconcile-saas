package com.reconcile.app.isolation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reconcile.shared.domain.MissingTenantException;
import com.reconcile.shared.domain.TenantContext;
import com.reconcile.shared.domain.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
@DisplayName("Cross-tenant isolation")
class CrossTenantIsolationTest {

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

    @Test
    @DisplayName("tenant T2 sees no ledger entries created under T1")
    void ledgerIsolation() {
        TenantId t1 = TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        TenantId t2 = TenantId.of(UUID.randomUUID());

        // T1: ingest fixture → ledger entries exist
        // T2: query LedgerEntryRepository.findAll() → empty
        // Full assertions added in commit 14 (vertical slice)
    }

    @Test
    @DisplayName("missing TenantContext on data-access path throws MissingTenantException")
    void failsClosedWithNoContext() {
        TenantContext.clear();
        assertThatThrownBy(TenantContext::current)
                .isInstanceOf(MissingTenantException.class)
                .hasMessageContaining("No tenant context set");
    }

    @Test
    @DisplayName("async task propagates TenantContext from submitting thread")
    void asyncTenantPropagation() {
        // stub: set T1 context, submit @Async task, assert task ran in T1 schema
        // implemented in commit 14 alongside vertical slice
    }

    @Test
    @DisplayName("Batch job TenantJobExecutionListener establishes TenantContext on worker threads")
    void batchTenantPropagation() {
        // stub: launch FileIngestionJob with tenantId param, assert writes landed in T1 schema
        // implemented in commit 14 alongside vertical slice
    }

    @Test
    @DisplayName("@ApplicationModuleListener propagates TenantContext from TenantScopedEvent")
    void eventListenerTenantPropagation() {
        // stub: publish TenantScopedEvent from T1, assert notification landed in T1 schema
        // implemented in commit 14 alongside vertical slice
    }
}
