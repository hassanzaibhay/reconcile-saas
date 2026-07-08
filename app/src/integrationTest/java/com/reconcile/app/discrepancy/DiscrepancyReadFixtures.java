package com.reconcile.app.discrepancy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared raw-SQL seeding for the discrepancy read-endpoint tests. Every test below needs the same
 * FK-satisfying scaffold (match_run + ledger_entry rows) before it can insert a discrepancy with an
 * explicit {@code created_at}, so this is centralized rather than repeated per test class.
 */
final class DiscrepancyReadFixtures {

    private DiscrepancyReadFixtures() {}

    /** Runs {@code work} on a connection with {@code search_path} set to the tenant schema. */
    static void withSchema(JdbcTemplate jdbc, String schema, SchemaWork work) {
        jdbc.execute((ConnectionCallback<Void>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try {
                work.run(conn);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
            return null;
        });
    }

    static void insertMatchRun(Connection conn, UUID runId) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("INSERT INTO match_run (id, status) VALUES (?, 'COMPLETED')")) {
            ps.setObject(1, runId);
            ps.executeUpdate();
        }
    }

    static void insertLedgerEntry(Connection conn, UUID entryId, BigDecimal amount, String currency)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO ledger_entry"
                + " (id, feed_id, entry_date, amount, currency) VALUES (?, 'feed', DATE '2026-01-01', ?, ?)")) {
            ps.setObject(1, entryId);
            ps.setBigDecimal(2, amount);
            ps.setString(3, currency);
            ps.executeUpdate();
        }
    }

    /** {@code createdAt} null → column default ({@code now()}); non-null → explicit microsecond-precision value. */
    static void insertUnmatchedDiscrepancy(
            Connection conn, UUID id, UUID runId, UUID entryId, String status, Instant createdAt) throws SQLException {
        String sql = createdAt == null
                ? "INSERT INTO discrepancy (id, match_run_id, type, unmatched_entry_id, status) VALUES (?, ?, 'UNMATCHED', ?, ?)"
                : "INSERT INTO discrepancy (id, match_run_id, type, unmatched_entry_id, status, created_at)"
                        + " VALUES (?, ?, 'UNMATCHED', ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, runId);
            ps.setObject(3, entryId);
            ps.setString(4, status);
            if (createdAt != null) {
                ps.setTimestamp(
                        5,
                        Timestamp.from(createdAt),
                        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
            }
            ps.executeUpdate();
        }
    }

    static void insertAmbiguousDiscrepancy(Connection conn, UUID id, UUID runId, String status, Instant createdAt)
            throws SQLException {
        String sql = createdAt == null
                ? "INSERT INTO discrepancy (id, match_run_id, type, status) VALUES (?, ?, 'AMBIGUOUS', ?)"
                : "INSERT INTO discrepancy (id, match_run_id, type, status, created_at) VALUES (?, ?, 'AMBIGUOUS', ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, runId);
            ps.setString(3, status);
            if (createdAt != null) {
                ps.setTimestamp(
                        4,
                        Timestamp.from(createdAt),
                        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
            }
            ps.executeUpdate();
        }
    }

    static void insertClusterMembers(Connection conn, UUID discrepancyId, List<UUID> memberEntryIds)
            throws SQLException {
        for (UUID memberId : memberEntryIds) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ambiguous_cluster_member (discrepancy_id, ledger_entry_id) VALUES (?, ?)")) {
                ps.setObject(1, discrepancyId);
                ps.setObject(2, memberId);
                ps.executeUpdate();
            }
        }
    }

    @FunctionalInterface
    interface SchemaWork {
        void run(Connection conn) throws SQLException;
    }
}
