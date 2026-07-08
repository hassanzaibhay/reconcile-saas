package com.reconcile.app.discrepancy;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reconcile.app.support.TenantTest;
import com.reconcile.shared.domain.TenantId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** GET /api/v1/discrepancies/{id} — UNMATCHED shape, AMBIGUOUS shape, and missing id. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TenantTest
@DisplayName("Discrepancy detail")
class DiscrepancyDetailTest {

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
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("existing UNMATCHED discrepancy → 200 with amount/currency, empty clusterMembers")
    void unmatchedDetail(TenantId tenantId) throws Exception {
        String schema = tenantId.schemaName();
        UUID runId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        UUID discId = UUID.randomUUID();

        DiscrepancyReadFixtures.withSchema(jdbcTemplate, schema, conn -> {
            DiscrepancyReadFixtures.insertMatchRun(conn, runId);
            DiscrepancyReadFixtures.insertLedgerEntry(conn, entryId, new BigDecimal("42.50"), "USD");
            DiscrepancyReadFixtures.insertUnmatchedDiscrepancy(conn, discId, runId, entryId, "OPEN", null);
        });

        getDetailAs(tenantId, discId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(discId.toString()))
                .andExpect(jsonPath("$.type").value("UNMATCHED"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.unmatchedEntryId").value(entryId.toString()))
                .andExpect(jsonPath("$.amount").value(42.50))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.clusterMembers").isEmpty());
    }

    @Test
    @DisplayName("existing AMBIGUOUS discrepancy → 200 with cluster members, null amount/currency")
    void ambiguousDetail(TenantId tenantId) throws Exception {
        String schema = tenantId.schemaName();
        UUID runId = UUID.randomUUID();
        UUID discId = UUID.randomUUID();
        UUID memberA = UUID.randomUUID();
        UUID memberB = UUID.randomUUID();

        DiscrepancyReadFixtures.withSchema(jdbcTemplate, schema, conn -> {
            DiscrepancyReadFixtures.insertMatchRun(conn, runId);
            DiscrepancyReadFixtures.insertLedgerEntry(conn, memberA, new BigDecimal("1.00"), "USD");
            DiscrepancyReadFixtures.insertLedgerEntry(conn, memberB, new BigDecimal("2.00"), "USD");
            DiscrepancyReadFixtures.insertAmbiguousDiscrepancy(conn, discId, runId, "OPEN", null);
            DiscrepancyReadFixtures.insertClusterMembers(conn, discId, List.of(memberA, memberB));
        });

        getDetailAs(tenantId, discId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.unmatchedEntryId").doesNotExist())
                .andExpect(jsonPath("$.amount").doesNotExist())
                .andExpect(jsonPath(
                        "$.clusterMembers",
                        org.hamcrest.Matchers.containsInAnyOrder(memberA.toString(), memberB.toString())));
    }

    @Test
    @DisplayName("missing id → 404 ApiError")
    void missingIdIs404(TenantId tenantId) throws Exception {
        getDetailAs(tenantId, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.traceId").exists());
    }

    private org.springframework.test.web.servlet.ResultActions getDetailAs(TenantId tenantId, UUID discrepancyId)
            throws Exception {
        return mockMvc.perform(get("/api/v1/discrepancies/{id}", discrepancyId)
                .with(jwt().jwt(b -> b.claim("tid", tenantId.value().toString()).subject("operator@test"))));
    }
}
