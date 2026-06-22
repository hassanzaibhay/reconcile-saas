package com.reconcile.app.isolation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reconcile.shared.domain.MissingTenantException;
import com.reconcile.shared.domain.TenantContext;
import com.reconcile.shared.domain.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Cross-tenant isolation contract tests.
 *
 * <p>INITIALLY @Disabled — no entities exist yet (ledger/ingestion written in later commits). These
 * tests document the isolation contract and will be unskipped in commit 13 once all modules are
 * present and the full @SpringBootTest context wires up.
 *
 * <p>The test bodies are intentionally incomplete stubs showing the assertion shape. Real
 * assertions are added incrementally alongside each module commit.
 */
@Disabled(
        "Unskip in commit 13 after all modules present. Test bodies are stubs — assertions grow"
                + " alongside module commits.")
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Cross-tenant isolation")
class CrossTenantIsolationTest {

    @Test
    @DisplayName("tenant T2 sees no ledger entries created under T1")
    void ledgerIsolation() {
        TenantId t1 = TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        TenantId t2 = TenantId.of(UUID.randomUUID());

        // T1: ingest fixture → ledger entries exist
        // T2: query LedgerEntryRepository.findAll() → empty
        // (stubs — filled in alongside ledger/ingestion modules)
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
        // implemented alongside iam/AsyncConfig test in commit 13
    }

    @Test
    @DisplayName("Batch job TenantJobExecutionListener establishes TenantContext on worker threads")
    void batchTenantPropagation() {
        // stub: launch FileIngestionJob with tenantId param, assert writes landed in T1 schema
        // implemented alongside ingestion module in commit 13
    }

    @Test
    @DisplayName("@ApplicationModuleListener propagates TenantContext from TenantScopedEvent")
    void eventListenerTenantPropagation() {
        // stub: publish TenantScopedEvent from T1, assert notification landed in T1 schema
        // implemented alongside notification module in commit 13
    }
}
