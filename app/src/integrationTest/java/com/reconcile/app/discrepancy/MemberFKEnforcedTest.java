package com.reconcile.app.discrepancy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reconcile.app.support.TenantTest;
import com.reconcile.shared.domain.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
@DisplayName("MemberFKEnforced — fk_acm_entry rejects non-existent ledger_entry_id at DB level")
class MemberFKEnforcedTest {

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

    @Test
    @DisplayName("ambiguous_cluster_member with non-existent ledger_entry_id → FK violation from DB, not app")
    void nonExistentEntryIdRejectedByFK(TenantId tenantId) {
        String schema = tenantId.schemaName();
        UUID runId = UUID.randomUUID();
        UUID discrepancyId = UUID.randomUUID();

        // insert prerequisites
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try {
                conn.createStatement()
                        .execute("INSERT INTO match_run(id, status, matched_count, unmatched_count)" + " VALUES ('"
                                + runId + "', 'COMPLETED', 0, 0)");
                conn.createStatement()
                        .execute("INSERT INTO discrepancy(id, match_run_id, type, unmatched_entry_id,"
                                + " created_at) VALUES ('"
                                + discrepancyId + "','" + runId + "','AMBIGUOUS', NULL, now())");
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
            return null;
        });

        // non-existent ledger_entry_id — DB must reject with FK violation
        UUID ghostEntryId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
                    conn.createStatement().execute("SET search_path TO " + schema);
                    try {
                        conn.createStatement()
                                .execute("INSERT INTO ambiguous_cluster_member(discrepancy_id, ledger_entry_id)"
                                        + " VALUES ('" + discrepancyId + "','" + ghostEntryId + "')");
                    } finally {
                        conn.createStatement().execute("RESET search_path");
                    }
                    return null;
                }))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
