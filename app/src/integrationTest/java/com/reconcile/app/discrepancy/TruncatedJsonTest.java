package com.reconcile.app.discrepancy;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TenantTest
@DisplayName("TruncatedJson — a structurally broken JSON body is rejected with 400")
class TruncatedJsonTest {

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

    @Test
    @DisplayName("truncated JSON body → 400, not 500")
    void truncatedJsonRejected(TenantId tenantId) throws Exception {
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

        mockMvc.perform(post("/api/v1/discrepancies/{id}/resolve", discrepancyId)
                        .with(jwt().jwt(builder -> builder.claim(
                                        "tid", tenantId.value().toString())
                                .subject("operator@test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairings\":["))
                .andExpect(status().isBadRequest());
    }
}
