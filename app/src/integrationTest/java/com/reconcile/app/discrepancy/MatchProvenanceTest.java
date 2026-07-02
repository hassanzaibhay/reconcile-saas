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
@DisplayName("MatchProvenance — match_result.rule_id is authoritative for both rule types")
class MatchProvenanceTest {

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
    @DisplayName("exact-rule match → match_result.rule_id = 'EXACT_AMOUNT_AND_DATE'")
    void exactRuleProvenanceRecorded(TenantId tenantId) {
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

        String ruleId = queryMatchResultRuleId(tenantId.schemaName(), runId);
        assertThat(ruleId).isEqualTo(ExactAmountAndDateRule.RULE_ID);
    }

    @Test
    @DisplayName("tolerance-rule match → match_result.rule_id = 'AMOUNT_TOLERANCE_DATE_DRIFT'")
    void toleranceRuleProvenanceRecorded(TenantId tenantId) {
        Currency usd = Currency.getInstance("USD");
        LocalDate date = LocalDate.of(2025, 6, 2);
        // +100.01 and -100.00: sum=+0.01 (not exact), |sum|=0.01 ≤ abs tolerance 0.01
        // 100.005 would round to 100.00 via HALF_EVEN (banker's rounding), making it an exact match
        LedgerEntry left = LedgerEntry.create(
                "feed-bank", date, Money.of(new BigDecimal("100.01"), usd), "", "", UUID.randomUUID());
        LedgerEntry right = LedgerEntry.create(
                "feed-proc", date, Money.of(new BigDecimal("-100.00"), usd), "", "", UUID.randomUUID());
        ledgerEntryRepository.saveAll(List.of(left, right));

        ToleranceConfig cfg =
                new ToleranceConfig(UUID.randomUUID(), new BigDecimal("0.01"), new BigDecimal("0.001"), 0);
        MatchRunId runId = MatchRunId.generate();
        MatchRunResult result = orchestrator.orchestrate(
                runId,
                List.of(new ExactAmountAndDateRule(), new AmountToleranceDateDriftRule(cfg)),
                List.of(left, right));

        assertThat(result.matches()).hasSize(1);

        String ruleId = queryMatchResultRuleId(tenantId.schemaName(), runId);
        assertThat(ruleId).isEqualTo(AmountToleranceDateDriftRule.RULE_ID);
    }

    private String queryMatchResultRuleId(String schema, MatchRunId runId) {
        return jdbcTemplate.execute((ConnectionCallback<String>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try (ResultSet rs = conn.prepareStatement(
                            "SELECT rule_id FROM match_result WHERE match_run_id = '" + runId.value() + "'")
                    .executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString("rule_id");
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
        });
    }
}
