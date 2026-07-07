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

/**
 * Positive proof of the sub-slice-3 orchestrator reopen: an engine match, driven through the real
 * {@code ReconciliationOrchestrator -> JpaMatchResultRepository.saveAll} path (not a raw-SQL
 * fixture), must co-write exactly 2 {@code matched_entry} rows sharing one {@code match_result_id}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TenantTest
@DisplayName("MatchedEntryCoWrite — engine match co-writes matched_entry availability rows")
class MatchedEntryCoWriteTest {

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
    @DisplayName(
            "exact-rule match via orchestrator.orchestrate → 2 matched_entry rows, 1 match_result_id, correct entry ids")
    void engineMatchCoWritesMatchedEntry(TenantId tenantId) {
        Currency usd = Currency.getInstance("USD");
        LocalDate date = LocalDate.of(2025, 6, 1);
        LedgerEntry credit = LedgerEntry.create(
                "feed-bank", date, Money.of(new BigDecimal("500.00"), usd), "", "", UUID.randomUUID());
        LedgerEntry debit = LedgerEntry.create(
                "feed-proc", date, Money.of(new BigDecimal("-500.00"), usd), "", "", UUID.randomUUID());
        ledgerEntryRepository.saveAll(List.of(credit, debit));

        MatchRunId runId = MatchRunId.generate();
        MatchRunResult result =
                orchestrator.orchestrate(runId, List.of(new ExactAmountAndDateRule()), List.of(credit, debit));
        assertThat(result.matches()).hasSize(1);

        String schema = tenantId.schemaName();
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try {
                UUID matchResultId;
                try (ResultSet rs = conn.prepareStatement(
                                "SELECT id FROM match_result WHERE match_run_id = '" + runId.value() + "'")
                        .executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    matchResultId = (UUID) rs.getObject("id");
                    assertThat(rs.next()).isFalse();
                }

                try (ResultSet rs = conn.prepareStatement("SELECT ledger_entry_id, match_result_id FROM matched_entry"
                                + " WHERE match_result_id = '" + matchResultId + "'")
                        .executeQuery()) {
                    List<UUID> entryIds = new java.util.ArrayList<>();
                    while (rs.next()) {
                        assertThat(rs.getObject("match_result_id")).isEqualTo(matchResultId);
                        entryIds.add((UUID) rs.getObject("ledger_entry_id"));
                    }
                    assertThat(entryIds)
                            .containsExactlyInAnyOrder(
                                    credit.id().value(), debit.id().value());
                }
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
            return null;
        });
    }
}
