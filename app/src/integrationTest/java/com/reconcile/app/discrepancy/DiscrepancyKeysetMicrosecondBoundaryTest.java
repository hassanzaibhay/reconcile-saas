package com.reconcile.app.discrepancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcile.app.support.TenantTest;
import com.reconcile.shared.domain.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.HashSet;
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
 * CO-HEADLINE A. Two discrepancies exactly 1 microsecond apart, fetched across two {@code limit=1}
 * requests. Proves the {@code (created_at, id) < (cursor)} predicate's boundary neither skips nor
 * repeats a row at the finest precision the column supports.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TenantTest
@DisplayName("Discrepancy list — microsecond keyset boundary (headline)")
class DiscrepancyKeysetMicrosecondBoundaryTest {

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
    @DisplayName("neither row skipped nor repeated across a limit=1 boundary 1us apart")
    void microsecondBoundaryNeitherSkipsNorRepeats(TenantId tenantId) throws Exception {
        String schema = tenantId.schemaName();
        UUID runId = UUID.randomUUID();
        UUID entryEarlier = UUID.randomUUID();
        UUID entryLater = UUID.randomUUID();
        UUID discEarlier = UUID.randomUUID();
        UUID discLater = UUID.randomUUID();

        Instant base = Instant.parse("2026-01-01T00:00:00.000001Z");
        Instant earlier = base;
        Instant later = base.plusNanos(1000); // +1 microsecond

        DiscrepancyReadFixtures.withSchema(jdbcTemplate, schema, conn -> {
            DiscrepancyReadFixtures.insertMatchRun(conn, runId);
            DiscrepancyReadFixtures.insertLedgerEntry(
                    conn,
                    entryEarlier,
                    new BigDecimal("10.00"),
                    Currency.getInstance("USD").getCurrencyCode());
            DiscrepancyReadFixtures.insertLedgerEntry(
                    conn,
                    entryLater,
                    new BigDecimal("20.00"),
                    Currency.getInstance("USD").getCurrencyCode());
            DiscrepancyReadFixtures.insertUnmatchedDiscrepancy(conn, discEarlier, runId, entryEarlier, "OPEN", earlier);
            DiscrepancyReadFixtures.insertUnmatchedDiscrepancy(conn, discLater, runId, entryLater, "OPEN", later);
        });

        JsonNode page1 = fetchPage(tenantId, null);
        assertThat(page1.get("items")).hasSize(1);
        assertThat(page1.get("hasMore").asBoolean()).isTrue();
        UUID firstId = UUID.fromString(page1.get("items").get(0).get("id").asText());
        assertThat(firstId).isEqualTo(discLater); // DESC order: later created_at first

        String cursor = page1.get("nextCursor").asText();
        JsonNode page2 = fetchPage(tenantId, cursor);
        assertThat(page2.get("items")).hasSize(1);
        assertThat(page2.get("hasMore").asBoolean()).isFalse();
        assertThat(page2.get("nextCursor").isNull()).isTrue();
        UUID secondId = UUID.fromString(page2.get("items").get(0).get("id").asText());
        assertThat(secondId).isEqualTo(discEarlier);

        Set<UUID> seen = new HashSet<>(Set.of(firstId, secondId));
        assertThat(seen).containsExactlyInAnyOrder(discEarlier, discLater);
    }

    private JsonNode fetchPage(TenantId tenantId, String cursor) throws Exception {
        var requestBuilder = get("/api/v1/discrepancies")
                .param("limit", "1")
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
