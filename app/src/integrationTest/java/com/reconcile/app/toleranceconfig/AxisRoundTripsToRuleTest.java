package com.reconcile.app.toleranceconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.reconcile.app.support.TenantTest;
import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.reconciliation.domain.AmountToleranceDateDriftRule;
import com.reconcile.reconciliation.domain.MatchingAxis;
import com.reconcile.reconciliation.domain.ToleranceConfig;
import com.reconcile.reconciliation.domain.ToleranceConfigRepository;
import com.reconcile.shared.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
@DisplayName("AxisRoundTripsToRule — persisted axis changes matching semantics")
class AxisRoundTripsToRuleTest {

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
    ToleranceConfigRepository repository;

    @Test
    @DisplayName("DIFFERENCE axis persisted and loaded: same-sign pair (+100/+99.97) within tol=0.05;"
            + " SUM_TO_ZERO axis would reject same pair; real rule returns entryB as neighbor of entryA")
    void persistedDifferenceAxisChangesBehavior() {
        Currency usd = Currency.getInstance("USD");
        Money a = Money.of(new BigDecimal("100.00"), usd);
        Money b = Money.of(new BigDecimal("99.97"), usd);
        BigDecimal tolerance = new BigDecimal("0.05");

        repository.save(
                new ToleranceConfig(UUID.randomUUID(), tolerance, new BigDecimal("0.001"), 0, MatchingAxis.DIFFERENCE));

        ToleranceConfig loaded = repository.findCurrent();

        assertThat(loaded.axis()).as("axis round-trips through persistence").isEqualTo(MatchingAxis.DIFFERENCE);

        // amountDiff-level check: same math the rule uses internally
        BigDecimal diffAxisDiff = loaded.axis().amountDiff(a, b);
        assertThat(diffAxisDiff)
                .as("DIFFERENCE axis: |100 - 99.97| = 0.03 ≤ tol 0.05 → adjacent")
                .isLessThanOrEqualTo(loaded.absoluteTolerance());

        BigDecimal sumToZeroDiff = MatchingAxis.SUM_TO_ZERO.amountDiff(a, b);
        assertThat(sumToZeroDiff)
                .as("SUM_TO_ZERO axis: |100 + 99.97| = 199.97 >> tol → not adjacent")
                .isGreaterThan(loaded.absoluteTolerance());

        // End-to-end: persisted axis drives the actual rule, not just amountDiff().
        // entryA (bank) and entryB (ledger) are same-sign, different feeds, same date —
        // adjacency is purely axis-dependent. Under SUM_TO_ZERO the rule would NOT return
        // entryB (|100+99.97|=199.97 >> 0.05), so a wrong-default load would fail this.
        LocalDate date = LocalDate.of(2026, 1, 15);
        LedgerEntry entryA = LedgerEntry.create("bank", date, a, "bank entry", "A-1", UUID.randomUUID());
        LedgerEntry entryB = LedgerEntry.create("ledger", date, b, "ledger entry", "B-1", UUID.randomUUID());

        AmountToleranceDateDriftRule rule = new AmountToleranceDateDriftRule(loaded);
        List<LedgerEntry> pool = List.of(entryA, entryB);
        assertThat(rule.neighbors(entryA, pool))
                .as("rule built from loaded DIFFERENCE config must see entryB as neighbor of entryA")
                .contains(entryB);
    }
}
