package com.reconcile.app.discrepancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
@DisplayName("AmbiguousRunPersists — full engine run with 3-way cluster completes and fixes live CHECK bug")
class AmbiguousRunPersistsTest {

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
    @DisplayName("3-way cluster run does not throw (V3 CHECK would have rejected AMBIGUOUS audit rows)")
    void ambiguousRunCompletesWithoutException(TenantId tenantId) {
        Currency usd = Currency.getInstance("USD");
        LocalDate date = LocalDate.of(2025, 3, 1);
        LedgerEntry a =
                LedgerEntry.create("feed-a", date, Money.of(new BigDecimal("200.00"), usd), "", "", UUID.randomUUID());
        LedgerEntry b =
                LedgerEntry.create("feed-b", date, Money.of(new BigDecimal("-200.00"), usd), "", "", UUID.randomUUID());
        LedgerEntry c =
                LedgerEntry.create("feed-c", date, Money.of(new BigDecimal("-200.00"), usd), "", "", UUID.randomUUID());
        ledgerEntryRepository.saveAll(List.of(a, b, c));

        MatchRunId runId = MatchRunId.generate();

        assertThatCode(() -> orchestrator.orchestrate(runId, List.of(new ExactAmountAndDateRule()), List.of(a, b, c)))
                .doesNotThrowAnyException();

        String schema = tenantId.schemaName();
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try {
                // V7 widened CHECK: 3 AMBIGUOUS audit_decision rows must exist
                try (ResultSet rs = conn.prepareStatement("SELECT COUNT(*) FROM audit_decision"
                                + " WHERE match_run_id = '" + runId.value()
                                + "' AND decision = 'AMBIGUOUS'")
                        .executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(3);
                }
                // 1 AMBIGUOUS discrepancy row persisted
                try (ResultSet rs = conn.prepareStatement("SELECT COUNT(*) FROM discrepancy"
                                + " WHERE match_run_id = '" + runId.value()
                                + "' AND type = 'AMBIGUOUS'")
                        .executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
            return null;
        });
    }
}
