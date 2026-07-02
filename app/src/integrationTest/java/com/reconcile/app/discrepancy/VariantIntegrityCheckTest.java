package com.reconcile.app.discrepancy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reconcile.app.support.TenantTest;
import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.ledger.domain.LedgerEntryRepository;
import com.reconcile.shared.domain.Money;
import com.reconcile.shared.domain.TenantId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
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
@DisplayName("VariantIntegrityCheck — chk_disc_variant rejects mis-typed rows at DB level")
class VariantIntegrityCheckTest {

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
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("AMBIGUOUS + non-null unmatched_entry_id → chk_disc_variant violation")
    void ambiguousWithNonNullEntryRejected(TenantId tenantId) {
        LedgerEntry entry = LedgerEntry.create(
                "feed-a",
                LocalDate.of(2025, 1, 1),
                Money.of(new BigDecimal("50.00"), Currency.getInstance("USD")),
                "",
                "",
                UUID.randomUUID());
        ledgerEntryRepository.save(entry);

        String schema = tenantId.schemaName();
        UUID runId = UUID.randomUUID();
        insertMatchRun(schema, runId);

        assertThatThrownBy(() -> jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
                    conn.createStatement().execute("SET search_path TO " + schema);
                    try {
                        conn.createStatement()
                                .execute("INSERT INTO discrepancy(id, match_run_id, type, unmatched_entry_id,"
                                        + " created_at) VALUES ('"
                                        + UUID.randomUUID() + "','" + runId + "','AMBIGUOUS','"
                                        + entry.id().value() + "', now())");
                    } finally {
                        conn.createStatement().execute("RESET search_path");
                    }
                    return null;
                }))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("UNMATCHED + null unmatched_entry_id → chk_disc_variant violation")
    void unmatchedWithNullEntryRejected(TenantId tenantId) {
        String schema = tenantId.schemaName();
        UUID runId = UUID.randomUUID();
        insertMatchRun(schema, runId);

        assertThatThrownBy(() -> jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
                    conn.createStatement().execute("SET search_path TO " + schema);
                    try {
                        conn.createStatement()
                                .execute("INSERT INTO discrepancy(id, match_run_id, type, unmatched_entry_id,"
                                        + " created_at) VALUES ('"
                                        + UUID.randomUUID() + "','" + runId + "','UNMATCHED', NULL, now())");
                    } finally {
                        conn.createStatement().execute("RESET search_path");
                    }
                    return null;
                }))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertMatchRun(String schema, UUID runId) {
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try {
                conn.createStatement()
                        .execute("INSERT INTO match_run(id, status, matched_count, unmatched_count)" + " VALUES ('"
                                + runId + "', 'COMPLETED', 0, 0)");
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
            return null;
        });
    }
}
