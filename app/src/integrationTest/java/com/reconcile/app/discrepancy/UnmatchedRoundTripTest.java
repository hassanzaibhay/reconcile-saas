package com.reconcile.app.discrepancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.reconcile.app.support.TenantTest;
import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.ledger.domain.LedgerEntryRepository;
import com.reconcile.reconciliation.application.ReconciliationOrchestrator;
import com.reconcile.reconciliation.domain.*;
import com.reconcile.shared.domain.Money;
import com.reconcile.shared.domain.TenantId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
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

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TenantTest
@DisplayName("UnmatchedRoundTrip — Unmatched discrepancy persists with correct shape")
class UnmatchedRoundTripTest {

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
    LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    ReconciliationOrchestrator orchestrator;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("single entry with no counterpart → UNMATCHED row with correct unmatched_entry_id")
    void singleEntryPersistsAsUnmatched(TenantId tenantId) {
        LedgerEntry entry = LedgerEntry.create(
                "feed-a",
                LocalDate.of(2025, 1, 1),
                Money.of(new BigDecimal("100.00"), Currency.getInstance("USD")),
                "desc",
                "ref-1",
                UUID.randomUUID());
        ledgerEntryRepository.save(entry);

        MatchRunId runId = MatchRunId.generate();
        MatchRunResult result = orchestrator.orchestrate(runId, List.of(new ExactAmountAndDateRule()), List.of(entry));

        // domain result
        assertThat(result.discrepancies()).hasSize(1);
        assertThat(result.discrepancies().get(0).entryId()).isEqualTo(entry.id());

        // DB round-trip via raw JDBC with explicit schema
        String schema = tenantId.schemaName();
        UUID persistedEntryId = jdbcTemplate.execute((ConnectionCallback<UUID>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try (ResultSet rs = conn.prepareStatement(
                            "SELECT type, unmatched_entry_id FROM discrepancy WHERE match_run_id = '" + runId.value()
                                    + "'")
                    .executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("type")).isEqualTo("UNMATCHED");
                UUID id = (UUID) rs.getObject("unmatched_entry_id");
                assertThat(rs.next()).isFalse();
                return id;
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
        });
        assertThat(persistedEntryId).isEqualTo(entry.id().value());
    }
}
