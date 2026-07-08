package com.reconcile.app.isolation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reconcile.shared.domain.TenantContext;
import com.reconcile.shared.domain.TenantId;
import com.reconcile.tenant.application.TenantProvisioningService;
import com.reconcile.tenant.domain.Tenant;
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
 * Proves {@code ReportingController}'s native SQL is tenant-scoped through the full HTTP+JWT path
 * (bearer {@code tid} → {@code TenantFilter} → multitenant {@code SET search_path}). Leads with the
 * collision case: the SAME {@code runId} seeded in both tenants with different counts must return each
 * tenant's own counts — ruling out both "reads a fixed schema" and "unions schemas".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("ReportingController cross-tenant isolation (native SQL)")
class ReportingCrossTenantIsolationTest {

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

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("same runId, different data per tenant → each tenant sees only its own counts")
    void sameRunIdReturnsPerTenantCounts() throws Exception {
        Tenant a = tenantProvisioningService.provision("rpt-a-" + shortId());
        Tenant b = tenantProvisioningService.provision("rpt-b-" + shortId());
        UUID sharedRunId = UUID.randomUUID();

        seedRun(a.id(), sharedRunId, 2, 1);
        seedRun(b.id(), sharedRunId, 5, 3);

        getReportAs(a.id(), sharedRunId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedCount").value(2))
                .andExpect(jsonPath("$.unmatchedCount").value(1));

        getReportAs(b.id(), sharedRunId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedCount").value(5))
                .andExpect(jsonPath("$.unmatchedCount").value(3));
    }

    @Test
    @DisplayName("run existing only in A returns empty when queried as B (secondary, not-present case)")
    void runOnlyInAReturnsEmptyForB() throws Exception {
        Tenant a = tenantProvisioningService.provision("rpo-a-" + shortId());
        Tenant b = tenantProvisioningService.provision("rpo-b-" + shortId());
        UUID runOnlyInA = UUID.randomUUID();

        seedRun(a.id(), runOnlyInA, 4, 2);

        getReportAs(b.id(), runOnlyInA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedCount").value(0))
                .andExpect(jsonPath("$.unmatchedCount").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions getReportAs(TenantId tenant, UUID runId)
            throws Exception {
        return mockMvc.perform(get("/api/v1/reports/runs/{runId}", runId)
                .with(jwt().jwt(b -> b.claim("tid", tenant.value().toString()).subject("operator@test"))));
    }

    /** Seeds a match_run with {@code matchedCount} match_result rows and {@code unmatchedCount} UNMATCHED discrepancies. */
    private void seedRun(TenantId tenant, UUID runId, int matchedCount, int unmatchedCount) {
        String schema = tenant.schemaName();
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try {
                conn.prepareStatement("INSERT INTO match_run (id, status) VALUES ('" + runId + "', 'COMPLETED')")
                        .execute();
                for (int i = 0; i < matchedCount; i++) {
                    conn.prepareStatement("INSERT INTO match_result"
                                    + " (id, match_run_id, left_entry_id, right_entry_id, rule_id) VALUES ('"
                                    + UUID.randomUUID() + "', '" + runId + "', '" + UUID.randomUUID() + "', '"
                                    + UUID.randomUUID() + "', 'RULE')")
                            .execute();
                }
                for (int j = 0; j < unmatchedCount; j++) {
                    UUID entryId = UUID.randomUUID();
                    conn.prepareStatement("INSERT INTO ledger_entry (id, feed_id, entry_date, amount, currency)"
                                    + " VALUES ('" + entryId + "', 'feed', DATE '2026-01-01', 100.0000, 'USD')")
                            .execute();
                    conn.prepareStatement("INSERT INTO discrepancy (id, match_run_id, type, unmatched_entry_id)"
                                    + " VALUES ('" + UUID.randomUUID() + "', '" + runId + "', 'UNMATCHED', '" + entryId
                                    + "')")
                            .execute();
                }
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
            return null;
        });
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
