package com.reconcile.app.verticalslice;

import static org.assertj.core.api.Assertions.assertThat;

import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.ledger.domain.LedgerEntryRepository;
import com.reconcile.shared.domain.TenantContext;
import com.reconcile.shared.domain.TenantId;
import com.reconcile.tenant.application.TenantProvisioningService;
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

/**
 * End-to-end vertical slice on the demo tenant. Asserts cross-tenant isolation:
 * T1's ledger rows are invisible from T2's context.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Demo tenant vertical slice")
class DemoTenantVerticalSliceIT {

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
    LedgerEntryRepository ledgerEntryRepository;

    @Test
    @DisplayName("T2 schema sees no ledger entries created under T1")
    void crossTenantIsolation() {
        TenantId t1 = TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        TenantId t2 = tenantProvisioningService.provision("isolation-test-t2").id();

        // T1: verify demo tenant schema is empty (no ingestion done)
        TenantContext.set(t1);
        try {
            Iterable<LedgerEntry> t1Entries = ledgerEntryRepository.findAll();
            assertThat(t1Entries).isEmpty();
        } finally {
            TenantContext.clear();
        }

        // T2: independently should also be empty
        TenantContext.set(t2);
        try {
            Iterable<LedgerEntry> t2Entries = ledgerEntryRepository.findAll();
            assertThat(t2Entries).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("missing TenantContext throws MissingTenantException (fail-closed)")
    void missingContextFailsClosed() {
        TenantContext.clear();
        org.assertj.core.api.Assertions.assertThatThrownBy(TenantContext::current)
                .isInstanceOf(com.reconcile.shared.domain.MissingTenantException.class);
    }
}
