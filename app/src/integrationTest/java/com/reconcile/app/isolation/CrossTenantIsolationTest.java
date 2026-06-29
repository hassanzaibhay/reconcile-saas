package com.reconcile.app.isolation;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.reconcile.ingestion.application.TenantJobExecutionListener;
import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.ledger.domain.LedgerEntryRepository;
import com.reconcile.notification.application.MatchRunNotificationListener;
import com.reconcile.reconciliation.application.MatchRunCompletedEvent;
import com.reconcile.reconciliation.domain.MatchRunId;
import com.reconcile.shared.domain.MissingTenantException;
import com.reconcile.shared.domain.Money;
import com.reconcile.shared.domain.TenantContext;
import com.reconcile.tenant.application.TenantProvisioningService;
import com.reconcile.tenant.domain.Tenant;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
@DisplayName("Cross-tenant isolation")
class CrossTenantIsolationTest {

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
    LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    ThreadPoolTaskExecutor taskExecutor;

    @Autowired
    TenantJobExecutionListener tenantJobExecutionListener;

    @Autowired
    MatchRunNotificationListener matchRunNotificationListener;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------------------
    // Test 1: schema-level ledger isolation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T2 queries return zero T1 rows; T1 sees exactly its own rows")
    void ledgerIsolation() {
        Tenant t1 = tenantProvisioningService.provision("iso-ledger-t1-" + shortId());
        Tenant t2 = tenantProvisioningService.provision("iso-ledger-t2-" + shortId());

        Money credit = Money.of(new BigDecimal("500.00"), Currency.getInstance("USD"));

        TenantContext.set(t1.id());
        try {
            ledgerEntryRepository.saveAll(List.of(
                    LedgerEntry.create("bank", LocalDate.now(), credit, "T1 credit", "T1-R1", UUID.randomUUID()),
                    LedgerEntry.create(
                            "proc", LocalDate.now(), credit.negate(), "T1 debit", "T1-R2", UUID.randomUUID())));
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(t2.id());
        try {
            assertThat(ledgerEntryRepository.findAll())
                    .as("T2 schema must be empty — no T1 rows visible")
                    .isEmpty();
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(t1.id());
        try {
            assertThat(ledgerEntryRepository.findAll())
                    .as("T1 schema must contain exactly the 2 seeded rows")
                    .hasSize(2);
        } finally {
            TenantContext.clear();
        }
    }

    // -------------------------------------------------------------------------
    // Test 2: fail-closed — no tenant context → MissingTenantException via resolver
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MissingTenantException propagates through JPA resolver when context is unset")
    void failsClosedWithNoContext() {
        TenantContext.clear();
        // Exercises the post-startup resolver path:
        //   JwtTenantIdentifierResolver.resolveCurrentTenantIdentifier()
        //   → TenantContext.current() → throws MissingTenantException.
        // Before the started-flag gate this returned "public", silently routing to the registry schema.
        assertThatThrownBy(() -> ledgerEntryRepository.findAll())
                .satisfiesAnyOf(
                        e -> assertThat(e).isInstanceOf(MissingTenantException.class),
                        e -> assertThat(e).hasCauseInstanceOf(MissingTenantException.class),
                        e -> assertThat(e).hasRootCauseInstanceOf(MissingTenantException.class));
    }

    // -------------------------------------------------------------------------
    // Test 3: async propagation — TenantAwareTaskDecorator
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("@Async worker thread inherits submitting thread's TenantContext; write lands in T1, absent from T2")
    void asyncTenantPropagation() throws Exception {
        Tenant t1 = tenantProvisioningService.provision("iso-async-t1-" + shortId());
        Tenant t2 = tenantProvisioningService.provision("iso-async-t2-" + shortId());

        CompletableFuture<Void> done = new CompletableFuture<>();

        // Set T1 context on submitting thread; TenantAwareTaskDecorator captures it.
        TenantContext.set(t1.id());
        try {
            taskExecutor.submit(() -> {
                try {
                    // Worker thread must be in T1 schema via the decorator.
                    ledgerEntryRepository.save(LedgerEntry.create(
                            "async-feed",
                            LocalDate.now(),
                            Money.of(new BigDecimal("75.00"), Currency.getInstance("USD")),
                            "async entry",
                            "A-001",
                            UUID.randomUUID()));
                    done.complete(null);
                } catch (Exception e) {
                    done.completeExceptionally(e);
                }
            });
        } finally {
            TenantContext.clear();
        }

        done.get(10, TimeUnit.SECONDS);

        TenantContext.set(t1.id());
        try {
            assertThat(ledgerEntryRepository.findAll())
                    .as("T1 must contain the row written by the async worker")
                    .hasSize(1);
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(t2.id());
        try {
            assertThat(ledgerEntryRepository.findAll())
                    .as("T2 must see nothing — async worker must not have leaked into T2 schema")
                    .isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    // -------------------------------------------------------------------------
    // Test 4: batch propagation — TenantJobExecutionListener
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TenantJobExecutionListener sets TenantContext from job params; write lands in T1, absent from T2")
    void batchTenantPropagation() {
        Tenant t1 = tenantProvisioningService.provision("iso-batch-t1-" + shortId());
        Tenant t2 = tenantProvisioningService.provision("iso-batch-t2-" + shortId());

        JobParameters params = new JobParametersBuilder()
                .addString(TenantJobExecutionListener.TENANT_ID_PARAM, t1.id().toString())
                .toJobParameters();
        JobExecution execution = new JobExecution(new JobInstance(nextJobInstanceId(), "isolation-job"), params);

        tenantJobExecutionListener.beforeJob(execution);
        try {
            // Any data access in this block runs against T1's schema.
            ledgerEntryRepository.save(LedgerEntry.create(
                    "batch-feed",
                    LocalDate.now(),
                    Money.of(new BigDecimal("300.00"), Currency.getInstance("USD")),
                    "batch entry",
                    "B-001",
                    UUID.randomUUID()));
        } finally {
            tenantJobExecutionListener.afterJob(execution);
        }

        TenantContext.set(t1.id());
        try {
            assertThat(ledgerEntryRepository.findAll())
                    .as("T1 must contain the row written under the batch job context")
                    .hasSize(1);
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(t2.id());
        try {
            assertThat(ledgerEntryRepository.findAll())
                    .as("T2 must see nothing — batch context must not have leaked")
                    .isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    // -------------------------------------------------------------------------
    // Test 5: event listener propagation — TenantAwareEventListenerAdvice
    // -------------------------------------------------------------------------

    @Test
    @DisplayName(
            "@ApplicationModuleListener reads tenantId from TenantScopedEvent; notification lands in T1 audit_log, absent from T2")
    void eventListenerTenantPropagation() {
        Tenant t1 = tenantProvisioningService.provision("iso-event-t1-" + shortId());
        Tenant t2 = tenantProvisioningService.provision("iso-event-t2-" + shortId());

        MatchRunId runId = MatchRunId.generate();

        // Construct event with T1 as tenant origin.
        TenantContext.set(t1.id());
        MatchRunCompletedEvent event;
        try {
            event = new MatchRunCompletedEvent(runId);
        } finally {
            TenantContext.clear();
        }

        // Invoke via the proxy chain: TenantAwareEventListenerAdvice sets TenantContext from
        // event.tenantId(), then @Async (Spring Modulith 1.4.x) submits the write to the executor.
        // TenantAwareTaskDecorator propagates T1 to the worker thread; await() synchronises.
        matchRunNotificationListener.on(event);

        await().atMost(5, SECONDS).until(() -> countAuditLog(t1, "MATCH_RUN_COMPLETED") > 0);

        int t1Rows = countAuditLog(t1, "MATCH_RUN_COMPLETED");
        int t2Rows = countAuditLog(t2, "MATCH_RUN_COMPLETED");

        assertThat(t1Rows)
                .as("T1 audit_log must contain exactly 1 MATCH_RUN_COMPLETED notification")
                .isEqualTo(1);
        assertThat(t2Rows)
                .as("T2 audit_log must be empty — event context must not have leaked")
                .isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int countAuditLog(Tenant tenant, String action) {
        String schema = tenant.schemaName();
        return jdbcTemplate.execute((ConnectionCallback<Integer>) conn -> {
            conn.createStatement().execute("SET search_path TO " + schema);
            try (ResultSet rs = conn.prepareStatement("SELECT COUNT(*) FROM audit_log WHERE action = '" + action + "'")
                    .executeQuery()) {
                rs.next();
                return rs.getInt(1);
            } finally {
                conn.createStatement().execute("RESET search_path");
            }
        });
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static long nextJobInstanceId() {
        return System.nanoTime();
    }
}
