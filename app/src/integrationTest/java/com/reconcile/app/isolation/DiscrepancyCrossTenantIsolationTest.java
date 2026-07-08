package com.reconcile.app.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcile.shared.domain.TenantContext;
import com.reconcile.shared.domain.TenantId;
import com.reconcile.tenant.application.TenantProvisioningService;
import com.reconcile.tenant.domain.Tenant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Proves the discrepancy read endpoints are tenant-scoped through the full HTTP+JWT path, following the
 * same pattern as {@link ReportingCrossTenantIsolationTest}: the SAME {@code match_run_id} seeded with
 * different discrepancy counts in both tenants rules out both "reads a fixed schema" and "unions schemas".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Discrepancy read endpoints cross-tenant isolation")
class DiscrepancyCrossTenantIsolationTest {

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
    TenantProvisioningService tenantProvisioningService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("same runId, different discrepancy counts per tenant → each tenant's list shows only its own rows")
    void sameRunIdReturnsOnlyOwnRows() throws Exception {
        Tenant a = tenantProvisioningService.provision("disc-a-" + shortId());
        Tenant b = tenantProvisioningService.provision("disc-b-" + shortId());
        UUID sharedRunId = UUID.randomUUID();

        List<UUID> aIds = seedAmbiguousRows(a.id(), sharedRunId, 2);
        List<UUID> bIds = seedAmbiguousRows(b.id(), sharedRunId, 5);

        JsonNode aPage = fetchList(a.id());
        assertThat(idsOf(aPage)).containsExactlyInAnyOrderElementsOf(aIds);

        JsonNode bPage = fetchList(b.id());
        assertThat(idsOf(bPage)).containsExactlyInAnyOrderElementsOf(bIds);
    }

    @Test
    @DisplayName("discrepancy existing only in A → B cannot fetch it by id (404, not 403)")
    void crossTenantDetailIsNotFound() throws Exception {
        Tenant a = tenantProvisioningService.provision("disc-a-" + shortId());
        Tenant b = tenantProvisioningService.provision("disc-b-" + shortId());
        UUID runId = UUID.randomUUID();

        List<UUID> aIds = seedAmbiguousRows(a.id(), runId, 1);
        UUID discrepancyOnlyInA = aIds.get(0);

        mockMvc.perform(get("/api/v1/discrepancies/{id}", discrepancyOnlyInA)
                        .with(jwt().jwt(b2 ->
                                b2.claim("tid", b.id().value().toString()).subject("operator@test"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/discrepancies/{id}", discrepancyOnlyInA)
                        .with(jwt().jwt(b2 ->
                                b2.claim("tid", a.id().value().toString()).subject("operator@test"))))
                .andExpect(status().isOk());
    }

    private JsonNode fetchList(TenantId tenant) throws Exception {
        String body = mockMvc.perform(get("/api/v1/discrepancies")
                        .param("limit", "50")
                        .with(jwt().jwt(b ->
                                b.claim("tid", tenant.value().toString()).subject("operator@test"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private List<UUID> idsOf(JsonNode page) {
        return page.get("items").findValuesAsText("id").stream()
                .map(UUID::fromString)
                .toList();
    }

    private List<UUID> seedAmbiguousRows(TenantId tenant, UUID runId, int count) {
        String schema = tenant.schemaName();
        List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(UUID.randomUUID());
        }
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try {
                conn.prepareStatement("INSERT INTO match_run (id, status) VALUES ('" + runId + "', 'COMPLETED')")
                        .execute();
                for (UUID id : ids) {
                    conn.prepareStatement("INSERT INTO discrepancy (id, match_run_id, type, status)" + " VALUES ('" + id
                                    + "', '" + runId + "', 'AMBIGUOUS', 'OPEN')")
                            .execute();
                }
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
            return null;
        });
        return ids;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
