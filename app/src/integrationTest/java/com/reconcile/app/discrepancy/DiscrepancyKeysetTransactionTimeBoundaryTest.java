package com.reconcile.app.discrepancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcile.app.support.TenantTest;
import com.reconcile.shared.domain.TenantId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

/**
 * CO-HEADLINE B (the dominant real-world path). {@code discrepancy.created_at DEFAULT now()} — Postgres
 * {@code now()} is transaction-start time, constant across a transaction. A reconciliation run persists
 * all its discrepancies in one transaction, so in production nearly every intra-run pagination boundary
 * resolves entirely on the {@code id} tie-break, not on {@code created_at}. This test replicates that by
 * inserting all rows inside one explicit transaction (letting the column default supply one shared
 * {@code now()}) rather than hand-staggering timestamps.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TenantTest
@DisplayName("Discrepancy list — same-created_at (tx-time) id-tiebreak boundary (co-headline)")
class DiscrepancyKeysetTransactionTimeBoundaryTest {

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

    @Test
    @DisplayName("5 rows sharing one created_at, paginated limit=2 → every row exactly once, no skip/repeat")
    void identicalCreatedAtResolvesOnIdTiebreak(TenantId tenantId) throws Exception {
        String schema = tenantId.schemaName();
        UUID runId = UUID.randomUUID();
        List<UUID> discIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            discIds.add(UUID.randomUUID());
        }

        DiscrepancyReadFixtures.withSchema(jdbcTemplate, schema, conn -> {
            conn.setAutoCommit(false);
            DiscrepancyReadFixtures.insertMatchRun(conn, runId);
            // AMBIGUOUS rows carry no unmatched_entry_id, so no ledger_entry FK is needed for this shape.
            for (UUID discId : discIds) {
                DiscrepancyReadFixtures.insertAmbiguousDiscrepancy(conn, discId, runId, "OPEN", null);
            }
            conn.commit();
            conn.setAutoCommit(true);
        });

        // Sanity: every seeded row truly shares one created_at (proves the harness replicated tx-time, not
        // an accidental spread).
        DiscrepancyReadFixtures.withSchema(jdbcTemplate, schema, conn -> {
            try (var rs = conn.createStatement()
                    .executeQuery("SELECT COUNT(DISTINCT created_at) AS distinct_ts FROM discrepancy")) {
                rs.next();
                assertThat(rs.getInt("distinct_ts")).isEqualTo(1);
            }
        });

        Set<UUID> collected = new HashSet<>();
        String cursor = null;
        int pages = 0;
        do {
            JsonNode page = fetchPage(tenantId, cursor);
            for (JsonNode item : page.get("items")) {
                UUID id = UUID.fromString(item.get("id").asText());
                assertThat(collected.add(id))
                        .as("row %s must not repeat across pages", id)
                        .isTrue();
            }
            cursor = page.get("hasMore").asBoolean() ? page.get("nextCursor").asText() : null;
            pages++;
            assertThat(pages).isLessThanOrEqualTo(10); // guard against an infinite-loop regression
        } while (cursor != null);

        assertThat(collected).containsExactlyInAnyOrderElementsOf(discIds);
        assertThat(pages).isEqualTo(3); // 5 rows at limit=2 -> pages of 2, 2, 1
    }

    private JsonNode fetchPage(TenantId tenantId, String cursor) throws Exception {
        var requestBuilder = get("/api/v1/discrepancies")
                .param("limit", "2")
                .with(jwt().jwt(b -> b.claim("tid", tenantId.value().toString()).subject("operator@test")));
        if (cursor != null) {
            requestBuilder = requestBuilder.param("cursor", cursor);
        }
        String body = mockMvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }
}
