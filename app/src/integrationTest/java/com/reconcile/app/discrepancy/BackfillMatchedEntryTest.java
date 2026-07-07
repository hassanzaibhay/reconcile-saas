package com.reconcile.app.discrepancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reconcile.app.support.TenantTest;
import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.ledger.domain.LedgerEntryRepository;
import com.reconcile.shared.domain.Money;
import com.reconcile.shared.domain.TenantId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
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

/**
 * Targeted SQL test of the V8 backfill statement itself — NOT a Flyway-on-empty-schema check
 * (vacuous: {@code @TenantTest} provisions a fresh schema with zero match_result rows, so Flyway
 * running V8 during provisioning proves nothing about the backfill's copy behavior). Instead, this
 * seeds match_result rows via raw SQL and re-executes the exact backfill INSERT...SELECT statement.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TenantTest
@DisplayName("BackfillMatchedEntry — the V8 backfill statement copies matches and detects corruption")
class BackfillMatchedEntryTest {

    private static final String BACKFILL_SQL = "INSERT INTO matched_entry (ledger_entry_id, match_result_id) "
            + "SELECT left_entry_id, id FROM match_result WHERE match_run_id = '%s' "
            + "UNION ALL "
            + "SELECT right_entry_id, id FROM match_result WHERE match_run_id = '%s'";

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
    @DisplayName("2 seeded match_result rows → backfill copies both sides of both pairs into matched_entry")
    void backfillCopiesBothSidesOfEachMatch(TenantId tenantId) {
        Currency usd = Currency.getInstance("USD");
        LedgerEntry e1 = entry(usd);
        LedgerEntry e2 = entry(usd);
        LedgerEntry e3 = entry(usd);
        LedgerEntry e4 = entry(usd);
        ledgerEntryRepository.saveAll(List.of(e1, e2, e3, e4));

        String schema = tenantId.schemaName();
        UUID runId = UUID.randomUUID();

        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try {
                conn.createStatement()
                        .execute("INSERT INTO match_run(id, status, matched_count, unmatched_count)" + " VALUES ('"
                                + runId + "', 'COMPLETED', 2, 0)");
                insertMatchResult(conn, UUID.randomUUID(), runId, e1.id(), e2.id());
                insertMatchResult(conn, UUID.randomUUID(), runId, e3.id(), e4.id());

                conn.createStatement().execute(String.format(BACKFILL_SQL, runId, runId));
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
            return null;
        });

        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try (ResultSet rs = conn.prepareStatement("SELECT ledger_entry_id FROM matched_entry"
                            + " JOIN match_result mr ON mr.id = matched_entry.match_result_id"
                            + " WHERE mr.match_run_id = '" + runId + "'")
                    .executeQuery()) {
                List<UUID> ids = new java.util.ArrayList<>();
                while (rs.next()) ids.add((UUID) rs.getObject("ledger_entry_id"));
                assertThat(ids)
                        .containsExactlyInAnyOrder(
                                e1.id().value(),
                                e2.id().value(),
                                e3.id().value(),
                                e4.id().value());
                return null;
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
        });
    }

    @Test
    @DisplayName("pre-existing double-match (same entry as left_entry_id in two rows) → backfill raises 23505")
    void backfillDetectsPreExistingDoubleMatch(TenantId tenantId) {
        Currency usd = Currency.getInstance("USD");
        LedgerEntry doubled = entry(usd);
        LedgerEntry other1 = entry(usd);
        LedgerEntry other2 = entry(usd);
        ledgerEntryRepository.saveAll(List.of(doubled, other1, other2));

        String schema = tenantId.schemaName();
        UUID runId = UUID.randomUUID();

        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try {
                conn.createStatement()
                        .execute("INSERT INTO match_run(id, status, matched_count, unmatched_count)" + " VALUES ('"
                                + runId + "', 'COMPLETED', 2, 0)");
                // corruption: 'doubled' appears as left_entry_id in two separate match_result rows
                insertMatchResult(conn, UUID.randomUUID(), runId, doubled.id(), other1.id());
                insertMatchResult(conn, UUID.randomUUID(), runId, doubled.id(), other2.id());
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
            return null;
        });

        assertThatThrownBy(() -> jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
                    conn.createStatement().execute("SET search_path TO " + schema);
                    try {
                        conn.createStatement().execute(String.format(BACKFILL_SQL, runId, runId));
                    } finally {
                        conn.createStatement().execute("RESET search_path");
                    }
                    return null;
                }))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertMatchResult(
            java.sql.Connection conn, UUID id, UUID runId, LedgerEntryId left, LedgerEntryId right)
            throws java.sql.SQLException {
        conn.createStatement()
                .execute("INSERT INTO match_result(id, match_run_id, left_entry_id, right_entry_id, rule_id)"
                        + " VALUES ('" + id + "','" + runId + "','" + left.value() + "','" + right.value()
                        + "','EXACT_AMOUNT_AND_DATE')");
    }

    private LedgerEntry entry(Currency usd) {
        return LedgerEntry.create(
                "feed", LocalDate.of(2025, 1, 1), Money.of(new BigDecimal("10.00"), usd), "", "", UUID.randomUUID());
    }
}
