package com.reconcile.app.discrepancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.reconcile.app.support.TenantTest;
import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.ledger.domain.LedgerEntryRepository;
import com.reconcile.reconciliation.application.ReconciliationOrchestrator;
import com.reconcile.reconciliation.domain.*;
import com.reconcile.shared.domain.Money;
import com.reconcile.shared.domain.TenantId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
@DisplayName("AmbiguousRoundTrip — Ambiguous discrepancy and cluster members persist correctly")
class AmbiguousRoundTripTest {

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
    ReconciliationOrchestrator orchestrator;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("3-way contention → AMBIGUOUS row with null unmatched_entry_id and 3 sorted members")
    void threeWayClusterPersistsAsAmbiguous(TenantId tenantId) {
        // A(+100) is exact-adjacent to B(−100) and C(−100) — 3-node component
        Currency usd = Currency.getInstance("USD");
        LocalDate date = LocalDate.of(2025, 1, 1);
        LedgerEntry a =
                LedgerEntry.create("feed-a", date, Money.of(new BigDecimal("100.00"), usd), "", "", UUID.randomUUID());
        LedgerEntry b =
                LedgerEntry.create("feed-b", date, Money.of(new BigDecimal("-100.00"), usd), "", "", UUID.randomUUID());
        LedgerEntry c =
                LedgerEntry.create("feed-c", date, Money.of(new BigDecimal("-100.00"), usd), "", "", UUID.randomUUID());
        ledgerEntryRepository.saveAll(List.of(a, b, c));

        MatchRunId runId = MatchRunId.generate();
        MatchRunResult result =
                orchestrator.orchestrate(runId, List.of(new ExactAmountAndDateRule()), List.of(a, b, c));

        assertThat(result.matches()).isEmpty();
        assertThat(result.discrepancies()).isEmpty();
        assertThat(result.ambiguousClusters()).hasSize(1);
        List<LedgerEntryId> clusterMembers = result.ambiguousClusters().get(0).members();
        assertThat(clusterMembers).containsExactlyInAnyOrder(a.id(), b.id(), c.id());

        String schema = tenantId.schemaName();
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try {
                // discrepancy row: AMBIGUOUS, unmatched_entry_id IS NULL
                try (ResultSet rs = conn.prepareStatement(
                                "SELECT id, type, unmatched_entry_id FROM discrepancy WHERE match_run_id = '"
                                        + runId.value() + "'")
                        .executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("type")).isEqualTo("AMBIGUOUS");
                    assertThat(rs.getObject("unmatched_entry_id")).isNull();
                    UUID discrepancyId = (UUID) rs.getObject("id");
                    assertThat(rs.next()).isFalse();

                    // cluster members: 3 rows, sorted by UUID string order
                    try (ResultSet mrs = conn.prepareStatement("SELECT ledger_entry_id FROM ambiguous_cluster_member"
                                    + " WHERE discrepancy_id = '" + discrepancyId
                                    + "' ORDER BY ledger_entry_id::text")
                            .executeQuery()) {
                        List<UUID> persistedIds = new ArrayList<>();
                        while (mrs.next()) {
                            persistedIds.add((UUID) mrs.getObject("ledger_entry_id"));
                        }
                        assertThat(persistedIds).hasSize(3);

                        List<UUID> expectedSorted = clusterMembers.stream()
                                .map(LedgerEntryId::value)
                                .sorted(Comparator.comparing(UUID::toString))
                                .toList();
                        assertThat(persistedIds).isEqualTo(expectedSorted);
                    }
                }
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
            return null;
        });
    }
}
