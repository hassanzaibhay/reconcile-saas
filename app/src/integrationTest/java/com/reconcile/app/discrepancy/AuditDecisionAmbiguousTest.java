package com.reconcile.app.discrepancy;

import static org.assertj.core.api.Assertions.assertThatCode;
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
@DisplayName("AuditDecisionAmbiguous — widened CHECK accepts AMBIGUOUS, still rejects bogus values")
class AuditDecisionAmbiguousTest {

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
    @DisplayName("decision='AMBIGUOUS' accepted by widened CHECK constraint")
    void ambiguousDecisionAccepted(TenantId tenantId) {
        String schema = tenantId.schemaName();
        // audit_decision has no FK on match_run_id or entry_id — any UUID is fine
        assertThatCode(() -> jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
                    conn.createStatement().execute("SET search_path TO " + schema);
                    try {
                        conn.createStatement()
                                .execute("INSERT INTO audit_decision(id, match_run_id, rule_id, entry_id,"
                                        + " decision, decided_at, decided_by) VALUES ('"
                                        + UUID.randomUUID() + "','" + UUID.randomUUID()
                                        + "','test-rule','" + UUID.randomUUID()
                                        + "','AMBIGUOUS', now(), 'SYSTEM')");
                    } finally {
                        conn.createStatement().execute("RESET search_path");
                    }
                    return null;
                }))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("decision='BOGUS' rejected by CHECK constraint")
    void bogusDecisionRejected(TenantId tenantId) {
        String schema = tenantId.schemaName();
        assertThatThrownBy(() -> jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
                    conn.createStatement().execute("SET search_path TO " + schema);
                    try {
                        conn.createStatement()
                                .execute("INSERT INTO audit_decision(id, match_run_id, rule_id, entry_id,"
                                        + " decision, decided_at, decided_by) VALUES ('"
                                        + UUID.randomUUID() + "','" + UUID.randomUUID()
                                        + "','test-rule','" + UUID.randomUUID()
                                        + "','BOGUS', now(), 'SYSTEM')");
                    } finally {
                        conn.createStatement().execute("RESET search_path");
                    }
                    return null;
                }))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
