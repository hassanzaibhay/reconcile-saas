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

/** Keyset traversal correctness at every boundary shape: empty, single-page, and exact-multiple-of-limit. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TenantTest
@DisplayName("Discrepancy list — traversal boundaries")
class DiscrepancyListTraversalTest {

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
    @DisplayName("no discrepancies seeded → empty items, hasMore=false, nextCursor=null")
    void emptyResult(TenantId tenantId) throws Exception {
        JsonNode page = fetchPage(tenantId, 10, null);
        assertThat(page.get("items")).isEmpty();
        assertThat(page.get("hasMore").asBoolean()).isFalse();
        assertThat(page.get("nextCursor").isNull()).isTrue();
    }

    @Test
    @DisplayName("fewer rows than limit → single page, hasMore=false")
    void singlePage(TenantId tenantId) throws Exception {
        List<UUID> ids = seedRows(tenantId, 3);

        JsonNode page = fetchPage(tenantId, 10, null);
        assertThat(page.get("items")).hasSize(3);
        assertThat(page.get("hasMore").asBoolean()).isFalse();
        assertThat(page.get("nextCursor").isNull()).isTrue();
        assertThat(idsOf(page)).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    @DisplayName("row count is an exact multiple of limit → last page still reports hasMore=false")
    void exactMultipleOfLimit(TenantId tenantId) throws Exception {
        List<UUID> ids = seedRows(tenantId, 4);

        JsonNode page1 = fetchPage(tenantId, 2, null);
        assertThat(page1.get("items")).hasSize(2);
        assertThat(page1.get("hasMore").asBoolean()).isTrue();

        JsonNode page2 = fetchPage(tenantId, 2, page1.get("nextCursor").asText());
        assertThat(page2.get("items")).hasSize(2);
        assertThat(page2.get("hasMore").asBoolean()).isFalse();
        assertThat(page2.get("nextCursor").isNull()).isTrue();

        Set<UUID> collected = new HashSet<>(idsOf(page1));
        collected.addAll(idsOf(page2));
        assertThat(collected).containsExactlyInAnyOrderElementsOf(ids);
    }

    private List<UUID> seedRows(TenantId tenantId, int count) {
        String schema = tenantId.schemaName();
        UUID runId = UUID.randomUUID();
        List<UUID> ids = new ArrayList<>();
        Instant base = Instant.parse("2026-02-01T00:00:00Z");
        DiscrepancyReadFixtures.withSchema(jdbcTemplate, schema, conn -> {
            DiscrepancyReadFixtures.insertMatchRun(conn, runId);
            for (int i = 0; i < count; i++) {
                UUID id = UUID.randomUUID();
                ids.add(id);
                DiscrepancyReadFixtures.insertAmbiguousDiscrepancy(conn, id, runId, "OPEN", base.plusSeconds(i));
            }
        });
        return ids;
    }

    private List<UUID> idsOf(JsonNode page) {
        List<UUID> ids = new ArrayList<>();
        for (JsonNode item : page.get("items")) {
            ids.add(UUID.fromString(item.get("id").asText()));
        }
        return ids;
    }

    private JsonNode fetchPage(TenantId tenantId, int limit, String cursor) throws Exception {
        var requestBuilder = get("/api/v1/discrepancies")
                .param("limit", String.valueOf(limit))
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
