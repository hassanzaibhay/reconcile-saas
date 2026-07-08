package com.reconcile.app.discrepancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcile.app.support.TenantTest;
import com.reconcile.shared.domain.TenantId;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** status/type/runId filter narrowing, AND-combination, unknown-value 400s, and filter/cursor mismatch 400. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TenantTest
@DisplayName("Discrepancy list — filters")
class DiscrepancyListFilterTest {

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

    @Autowired
    ObjectMapper objectMapper;

    private UUID runA;
    private UUID runB;
    private UUID openAmbiguous;
    private UUID resolvedAmbiguous;
    private UUID openUnmatchedInRunB;

    private void seed(TenantId tenantId) {
        String schema = tenantId.schemaName();
        runA = UUID.randomUUID();
        runB = UUID.randomUUID();
        openAmbiguous = UUID.randomUUID();
        resolvedAmbiguous = UUID.randomUUID();
        openUnmatchedInRunB = UUID.randomUUID();
        UUID entry = UUID.randomUUID();
        Instant base = Instant.parse("2026-03-01T00:00:00Z");

        DiscrepancyReadFixtures.withSchema(jdbcTemplate, schema, conn -> {
            DiscrepancyReadFixtures.insertMatchRun(conn, runA);
            DiscrepancyReadFixtures.insertMatchRun(conn, runB);
            DiscrepancyReadFixtures.insertLedgerEntry(conn, entry, new java.math.BigDecimal("5.00"), "USD");
            DiscrepancyReadFixtures.insertAmbiguousDiscrepancy(conn, openAmbiguous, runA, "OPEN", base);
            DiscrepancyReadFixtures.insertAmbiguousDiscrepancy(
                    conn, resolvedAmbiguous, runA, "RESOLVED", base.plusSeconds(1));
            DiscrepancyReadFixtures.insertUnmatchedDiscrepancy(
                    conn, openUnmatchedInRunB, runB, entry, "OPEN", base.plusSeconds(2));
        });
    }

    @Test
    @DisplayName("status filter narrows to matching rows only")
    void statusFilterNarrows(TenantId tenantId) throws Exception {
        seed(tenantId);
        JsonNode page = fetchPage(tenantId, "status", "RESOLVED", null, null);
        assertThat(idsOf(page)).containsExactly(resolvedAmbiguous);
    }

    @Test
    @DisplayName("type filter narrows to matching rows only")
    void typeFilterNarrows(TenantId tenantId) throws Exception {
        seed(tenantId);
        JsonNode page = fetchPage(tenantId, "type", "UNMATCHED", null, null);
        assertThat(idsOf(page)).containsExactly(openUnmatchedInRunB);
    }

    @Test
    @DisplayName("runId filter narrows to that run only")
    void runIdFilterNarrows(TenantId tenantId) throws Exception {
        seed(tenantId);
        JsonNode page = fetchPage(tenantId, "runId", runB.toString(), null, null);
        assertThat(idsOf(page)).containsExactly(openUnmatchedInRunB);
    }

    @Test
    @DisplayName("combined status+runId filters AND together")
    void combinedFiltersAnd(TenantId tenantId) throws Exception {
        seed(tenantId);
        JsonNode page = fetchPage(tenantId, "status", "OPEN", "runId", runA.toString());
        assertThat(idsOf(page)).containsExactly(openAmbiguous);
    }

    @Test
    @DisplayName("unknown status value → 400")
    void unknownStatusIsBadRequest(TenantId tenantId) throws Exception {
        seed(tenantId);
        mockMvc.perform(get("/api/v1/discrepancies")
                        .param("status", "BOGUS")
                        .with(jwt().jwt(b ->
                                b.claim("tid", tenantId.value().toString()).subject("operator@test"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("unknown type value → 400")
    void unknownTypeIsBadRequest(TenantId tenantId) throws Exception {
        seed(tenantId);
        mockMvc.perform(get("/api/v1/discrepancies")
                        .param("type", "BOGUS")
                        .with(jwt().jwt(b ->
                                b.claim("tid", tenantId.value().toString()).subject("operator@test"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("continuation cursor minted under different filters → 400")
    void filterMismatchedCursorIsBadRequest(TenantId tenantId) throws Exception {
        seed(tenantId);
        // OPEN yields 2 rows (openAmbiguous, openUnmatchedInRunB); request limit=1 to force a cursor.
        MvcResult result = mockMvc.perform(get("/api/v1/discrepancies")
                        .param("status", "OPEN")
                        .param("limit", "1")
                        .with(jwt().jwt(b ->
                                b.claim("tid", tenantId.value().toString()).subject("operator@test"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(page.get("hasMore").asBoolean()).isTrue();
        String cursorMintedUnderOpen = page.get("nextCursor").asText();

        mockMvc.perform(get("/api/v1/discrepancies")
                        .param("status", "RESOLVED")
                        .param("cursor", cursorMintedUnderOpen)
                        .with(jwt().jwt(b ->
                                b.claim("tid", tenantId.value().toString()).subject("operator@test"))))
                .andExpect(status().isBadRequest());
    }

    private List<UUID> idsOf(JsonNode page) {
        return page.get("items").findValuesAsText("id").stream()
                .map(UUID::fromString)
                .toList();
    }

    private JsonNode fetchPage(
            TenantId tenantId, String filterKey1, String filterVal1, String filterKey2, String filterVal2)
            throws Exception {
        var requestBuilder = get("/api/v1/discrepancies")
                .with(jwt().jwt(b -> b.claim("tid", tenantId.value().toString()).subject("operator@test")));
        if (filterKey1 != null) {
            requestBuilder = requestBuilder.param(filterKey1, filterVal1);
        }
        if (filterKey2 != null) {
            requestBuilder = requestBuilder.param(filterKey2, filterVal2);
        }
        String body = mockMvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }
}
