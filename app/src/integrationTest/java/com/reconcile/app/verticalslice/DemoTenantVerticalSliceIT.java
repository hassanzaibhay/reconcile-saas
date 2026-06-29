package com.reconcile.app.verticalslice;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.ledger.domain.LedgerEntryRepository;
import com.reconcile.reconciliation.application.DefaultMatchingEngine;
import com.reconcile.reconciliation.domain.ExactAmountAndDateRule;
import com.reconcile.reconciliation.domain.MatchRunId;
import com.reconcile.reconciliation.domain.MatchRunResult;
import com.reconcile.shared.domain.MissingTenantException;
import com.reconcile.shared.domain.Money;
import com.reconcile.shared.domain.TenantContext;
import com.reconcile.tenant.application.TenantProvisioningService;
import com.reconcile.tenant.domain.Tenant;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full pipeline: ingest → reconcile → report (audit_decision) → notification (audit_log).
 * Asserts cross-tenant isolation: T2 sees zero rows across all tables after T1's pipeline runs.
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

    @Autowired
    DefaultMatchingEngine matchingEngine;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------------------
    // Vertical slice: ingest → reconcile → report → notification + isolation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T2 schema sees no ledger entries created under T1")
    void crossTenantIsolation() {
        Tenant t1 = tenantProvisioningService.provision("vs-t1-" + shortId());
        Tenant t2 = tenantProvisioningService.provision("vs-t2-" + shortId());

        LocalDate txDate = LocalDate.of(2025, 6, 1);
        Money credit = Money.of(new BigDecimal("1000.00"), Currency.getInstance("USD"));
        UUID ingestionRunId = UUID.randomUUID();

        // ---- INGEST: seed T1 with a matched credit/debit pair from different feeds ----
        TenantContext.set(t1.id());
        try {
            ledgerEntryRepository.saveAll(List.of(
                    LedgerEntry.create("bank-feed", txDate, credit, "Bank credit", "B-001", ingestionRunId),
                    LedgerEntry.create(
                            "proc-feed", txDate, credit.negate(), "Processor debit", "P-001", ingestionRunId)));
        } finally {
            TenantContext.clear();
        }

        // ---- RECONCILE: run the matching engine against T1's entries ----
        MatchRunId matchRunId = MatchRunId.generate();
        MatchRunResult result;
        List<LedgerEntry> t1Entries;

        TenantContext.set(t1.id());
        try {
            t1Entries = ledgerEntryRepository.findAll();
            // run() is @Transactional; publishes MatchRunCompletedEvent after commit.
            // @ApplicationModuleListener is @Async in Spring Modulith 1.4.x — the notification
            // write happens in a background thread; await() below synchronises the assertion.
            result = matchingEngine.run(matchRunId, List.of(new ExactAmountAndDateRule()), t1Entries);
        } finally {
            TenantContext.clear();
        }

        // ---- REPORT: the engine matched the credit/debit pair ----
        assertThat(result.matches())
                .as("ExactAmountAndDateRule must match the one credit/debit pair")
                .hasSize(1);
        assertThat(result.discrepancies()).as("No unmatched entries expected").isEmpty();

        // Two audit_decision rows (one per entry, both MATCHED) must be in T1's schema.
        assertThat(countRows(t1, "audit_decision", "decision = 'MATCHED'"))
                .as("Two MATCHED audit_decision rows expected in T1")
                .isEqualTo(2);

        // ---- NOTIFICATION: MatchRunCompletedEvent → MatchRunNotificationListener ----
        // @ApplicationModuleListener dispatches asynchronously; wait up to 5 s for the row.
        await().atMost(5, SECONDS).until(() -> countRows(t1, "audit_log", "action = 'MATCH_RUN_COMPLETED'") > 0);

        assertThat(countRows(t1, "audit_log", "action = 'MATCH_RUN_COMPLETED'"))
                .as("Exactly 1 MATCH_RUN_COMPLETED notification must be in T1's audit_log")
                .isEqualTo(1);

        // ---- ISOLATION: T2 has zero rows across every table ----
        assertThat(countRows(t2, "ledger_entry", "1=1"))
                .as("T2 ledger_entry must be empty — T1 data must not leak")
                .isEqualTo(0);
        assertThat(countRows(t2, "audit_decision", "1=1"))
                .as("T2 audit_decision must be empty")
                .isEqualTo(0);
        assertThat(countRows(t2, "audit_log", "action = 'MATCH_RUN_COMPLETED'"))
                .as("T2 audit_log must be empty — notification must not leak")
                .isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Fail-closed guard — exercises resolver path, not TenantContext directly
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MissingTenantException propagates through JPA resolver when context is unset")
    void missingContextFailsClosed() {
        TenantContext.clear();
        assertThatThrownBy(() -> ledgerEntryRepository.findAll())
                .satisfiesAnyOf(
                        e -> assertThat(e).isInstanceOf(MissingTenantException.class),
                        e -> assertThat(e).hasCauseInstanceOf(MissingTenantException.class),
                        e -> assertThat(e).hasRootCauseInstanceOf(MissingTenantException.class));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private int countRows(Tenant tenant, String table, String where) {
        String schema = tenant.schemaName();
        return jdbcTemplate.execute((ConnectionCallback<Integer>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try (ResultSet rs =
                    conn.createStatement().executeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where)) {
                rs.next();
                return rs.getInt(1);
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
        });
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
