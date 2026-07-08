package com.reconcile.reconciliation.adapter.persistence;

import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.reconciliation.application.DiscrepancyForResolution;
import com.reconcile.reconciliation.application.DiscrepancyListQuery;
import com.reconcile.reconciliation.application.DiscrepancyListRow;
import com.reconcile.reconciliation.application.DiscrepancyQueryPort;
import com.reconcile.reconciliation.application.EntryMoney;
import com.reconcile.reconciliation.domain.AmbiguousCluster;
import com.reconcile.reconciliation.domain.MatchRunId;
import com.reconcile.shared.domain.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Repository;

/**
 * Read side of the {@code discrepancy} table. Two SQL shapes for the list query, deliberately not one
 * query with a {@code :hasCursor OR} branch: a single query serving both the first-page (no cursor) and
 * continuation (cursor) case forces the planner to pick a plan safe for both parameter states, which
 * under a generic plan (repeated execution, {@code plan_cache_mode} switch) drops the keyset index range
 * scan on the continuation path. Splitting the shapes keeps the continuation query's plan a deterministic
 * index range scan regardless of custom/generic planning.
 */
@Repository
class JpaDiscrepancyQueryAdapter implements DiscrepancyQueryPort {

    private static final String SELECT =
            "SELECT d.id, d.match_run_id, d.type, d.status, d.created_at, le.amount, le.currency "
                    + "FROM discrepancy d LEFT JOIN ledger_entry le ON le.id = d.unmatched_entry_id ";

    /**
     * Every placeholder is explicitly cast: an isolated {@code ? IS NULL} with no other usage gives
     * Postgres nothing to infer a parameter type from ("could not determine data type of parameter"),
     * since {@code NamedParameterJdbcTemplate} expands each repeated {@code :name} into its own
     * independent {@code $n} rather than one shared placeholder.
     */
    private static final String FILTERS =
            "AND (CAST(:status AS VARCHAR) IS NULL OR d.status = CAST(:status AS VARCHAR)) "
                    + "AND (CAST(:type AS VARCHAR) IS NULL OR d.type = CAST(:type AS VARCHAR)) "
                    + "AND (CAST(:runId AS UUID) IS NULL OR d.match_run_id = CAST(:runId AS UUID)) ";

    private static final String ORDER_LIMIT = "ORDER BY d.created_at DESC, d.id DESC LIMIT :limitPlusOne";

    private static final String FIRST_PAGE_SQL = SELECT + "WHERE TRUE " + FILTERS + ORDER_LIMIT;

    private static final String CONTINUATION_SQL =
            SELECT + "WHERE (d.created_at, d.id) < (:cursorCreatedAt, :cursorId) " + FILTERS + ORDER_LIMIT;

    private static final RowMapper<DiscrepancyListRow> ROW_MAPPER = (rs, rowNum) -> new DiscrepancyListRow(
            rs.getObject("id", UUID.class),
            rs.getObject("match_run_id", UUID.class),
            rs.getString("type"),
            rs.getString("status"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant().truncatedTo(ChronoUnit.MICROS),
            rs.getBigDecimal("amount"),
            rs.getString("currency"));

    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager em;

    JpaDiscrepancyQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DiscrepancyListRow> list(DiscrepancyListQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", query.status())
                .addValue("type", query.type())
                .addValue("runId", query.runId())
                .addValue("limitPlusOne", query.limitPlusOne());

        String sql;
        if (query.cursorCreatedAt() == null) {
            sql = FIRST_PAGE_SQL;
        } else {
            params.addValue("cursorCreatedAt", Timestamp.from(query.cursorCreatedAt()), Types.TIMESTAMP)
                    .addValue("cursorId", query.cursorId());
            sql = CONTINUATION_SQL;
        }
        return withTenantSchema(conn -> scopedJdbc(conn).query(sql, params, ROW_MAPPER));
    }

    /** Verbatim assembly moved from the former {@code JpaResolutionRepository.load} — behavior unchanged. */
    @Override
    public Optional<DiscrepancyForResolution> loadDetail(UUID discrepancyId) {
        DiscrepancyEntity entity = em.find(DiscrepancyEntity.class, discrepancyId);
        if (entity == null) {
            return Optional.empty();
        }

        AmbiguousCluster cluster = null;
        LedgerEntryId unmatchedEntryId = null;
        if ("AMBIGUOUS".equals(entity.type)) {
            List<UUID> memberIds = em.createQuery(
                            "SELECT m.ledgerEntryId FROM AmbiguousClusterMemberEntity m WHERE m.discrepancyId = :id",
                            UUID.class)
                    .setParameter("id", discrepancyId)
                    .getResultList();
            cluster = new AmbiguousCluster(
                    memberIds.stream().map(LedgerEntryId::of).toList());
        } else {
            unmatchedEntryId = LedgerEntryId.of(entity.unmatchedEntryId);
        }

        return Optional.of(new DiscrepancyForResolution(
                entity.id, MatchRunId.of(entity.matchRunId), entity.type, unmatchedEntryId, cluster, entity.status));
    }

    @Override
    public Optional<EntryMoney> findEntryMoney(UUID ledgerEntryId) {
        List<EntryMoney> rows = withTenantSchema(conn -> scopedJdbc(conn)
                .query(
                        "SELECT amount, currency FROM ledger_entry WHERE id = :id",
                        new MapSqlParameterSource("id", ledgerEntryId),
                        (rs, rowNum) -> new EntryMoney(rs.getBigDecimal("amount"), rs.getString("currency"))));
        return rows.stream().findFirst();
    }

    /**
     * Plain {@link JdbcTemplate} does not go through Hibernate's {@code MultiTenantConnectionProvider}
     * (that only fires for JPA/{@code EntityManager} access), so a native-SQL query issued through it
     * never gets {@code SET search_path} and fails closed against the default schema. This brackets one
     * borrowed connection with the same set/reset the provider does for JPA, scoped to the current
     * {@link TenantContext} — fail-closed if unset, per the tenant-isolation invariant.
     */
    private <T> T withTenantSchema(Function<Connection, T> work) {
        String schema = TenantContext.current().schemaName();
        return jdbcTemplate.execute((ConnectionCallback<T>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try {
                return work.apply(conn);
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
        });
    }

    /** Wraps the already-scoped connection so the query runs on it directly, not a different pooled one. */
    private static NamedParameterJdbcTemplate scopedJdbc(Connection conn) {
        return new NamedParameterJdbcTemplate(new SingleConnectionDataSource(conn, true));
    }
}
