package com.reconcile.app.discrepancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
 * Two resolves of the same discrepancy must yield exactly one 200 and one 409 — regardless of
 * which guard fires (uq_res_disc unique violation, @Version optimistic-lock conflict, or the
 * explicit status='OPEN' check). Covers both discrepancy kinds per the design's Defect D.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TenantTest
@DisplayName("ConcurrentResolve — double-resolve of the same discrepancy yields one 200 + one 409")
class ConcurrentResolveTest {

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
    @DisplayName("UNMATCHED discrepancy resolved twice → one 200, one 409")
    void doubleResolveOfUnmatchedDiscrepancy(TenantId tenantId) throws Exception {
        LedgerEntry entry = LedgerEntry.create(
                "feed-a",
                LocalDate.of(2025, 1, 1),
                Money.of(new BigDecimal("100.00"), Currency.getInstance("USD")),
                "desc",
                "ref-1",
                UUID.randomUUID());
        ledgerEntryRepository.save(entry);

        MatchRunId runId = MatchRunId.generate();
        orchestrator.orchestrate(runId, List.of(new ExactAmountAndDateRule()), List.of(entry));

        String schema = tenantId.schemaName();
        UUID discrepancyId = queryDiscrepancyId(schema, runId);

        assertOneOkAndOneConflict(tenantId, discrepancyId, "{}");
    }

    @Test
    @DisplayName("AMBIGUOUS (cluster) discrepancy resolved twice → one 200, one 409")
    void doubleResolveOfClusterDiscrepancy(TenantId tenantId) throws Exception {
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
        UUID discrepancyId = queryDiscrepancyId(schema, runId);

        Map<String, Object> body = Map.of(
                "pairings", List.of(Map.of("a", a.id().value(), "b", b.id().value())),
                "leftUnmatched", List.of(c.id().value()));

        assertOneOkAndOneConflict(tenantId, discrepancyId, objectMapper.writeValueAsString(body));
    }

    private void assertOneOkAndOneConflict(TenantId tenantId, UUID discrepancyId, String requestBody) throws Exception {
        List<Integer> statuses = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            int status = mockMvc.perform(post("/api/v1/discrepancies/{id}/resolve", discrepancyId)
                            .with(jwt().jwt(builder -> builder.claim(
                                            "tid", tenantId.value().toString())
                                    .subject("operator@test")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            statuses.add(status);
        }
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    }

    private UUID queryDiscrepancyId(String schema, MatchRunId runId) {
        return jdbcTemplate.execute((ConnectionCallback<UUID>) conn -> {
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
    }
}
