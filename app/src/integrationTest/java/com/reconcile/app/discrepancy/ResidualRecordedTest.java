package com.reconcile.app.discrepancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Focused check for Defect 1: the unmatched residual of a cluster resolution is RECORDED
 * (resolution_unmatched row) and stays AVAILABLE (no matched_entry row) — it is not silently
 * consumed and not re-discrepancied.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TenantTest
@DisplayName("ResidualRecorded — unmatched residual is recorded and stays available")
class ResidualRecordedTest {

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

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName(
            "residual entry: resolution_unmatched row exists, matched_entry row does not, no new Unmatched discrepancy")
    void residualStaysAvailableAndIsNotReDiscrepancied(TenantId tenantId) throws Exception {
        Currency usd = Currency.getInstance("USD");
        LocalDate date = LocalDate.of(2025, 1, 1);
        LedgerEntry a =
                LedgerEntry.create("feed-a", date, Money.of(new BigDecimal("100.00"), usd), "", "", UUID.randomUUID());
        LedgerEntry b =
                LedgerEntry.create("feed-b", date, Money.of(new BigDecimal("-100.00"), usd), "", "", UUID.randomUUID());
        LedgerEntry c =
                LedgerEntry.create("feed-c", date, Money.of(new BigDecimal("-100.00"), usd), "", "", UUID.randomUUID());
        ledgerEntryRepository.saveAll(List.of(a, b, c));

        MatchRunId runId = MatchRunId.generate();
        orchestrator.orchestrate(runId, List.of(new ExactAmountAndDateRule()), List.of(a, b, c));

        String schema = tenantId.schemaName();
        UUID discrepancyId = jdbcTemplate.execute((ConnectionCallback<UUID>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try (ResultSet rs = conn.prepareStatement(
                            "SELECT id FROM discrepancy WHERE match_run_id = '" + runId.value() + "'")
                    .executeQuery()) {
                rs.next();
                return (UUID) rs.getObject("id");
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
        });

        Map<String, Object> body = Map.of(
                "pairings", List.of(Map.of("a", a.id().value(), "b", b.id().value())),
                "leftUnmatched", List.of(c.id().value()));

        mockMvc.perform(post("/api/v1/discrepancies/{id}/resolve", discrepancyId)
                        .with(jwt().jwt(builder -> builder.claim(
                                        "tid", tenantId.value().toString())
                                .subject("operator@test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try {
                try (ResultSet rs = conn.prepareStatement("SELECT ledger_entry_id FROM resolution_unmatched ru"
                                + " JOIN resolution r ON r.id = ru.resolution_id"
                                + " WHERE r.discrepancy_id = '" + discrepancyId + "'")
                        .executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getObject("ledger_entry_id")).isEqualTo(c.id().value());
                    assertThat(rs.next()).isFalse();
                }

                try (ResultSet rs = conn.prepareStatement(
                                "SELECT count(*) AS cnt FROM matched_entry WHERE ledger_entry_id = '" + c.id().value()
                                        + "'")
                        .executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt("cnt")).isZero();
                }

                try (ResultSet rs = conn.prepareStatement(
                                "SELECT count(*) AS cnt FROM discrepancy WHERE unmatched_entry_id = '" + c.id().value()
                                        + "'")
                        .executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt("cnt")).isZero();
                }
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
            return null;
        });
    }
}
