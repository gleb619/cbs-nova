package cbs.nova.starter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.transaction.TransactionExecution;
import cbs.nova.starter.IntegrationTestApplication;
import cbs.nova.starter.service.DslRunRetentionPurger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Testcontainers
@SpringBootTest(classes = IntegrationTestApplication.class, properties = {
    "dsl.worker.enabled=false"})
class DslRunRetentionIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired
  private DslRunRepository repository;

  @Autowired
  private TransactionExecutionRepository transactionRepository;

  @Autowired
  private MeterRegistry meterRegistry;

  @Autowired
  private DataSource dataSource;

  @BeforeAll
  static void applyMigrations() throws SQLException {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/postgres/V1__init.sql"));
    populator.setContinueOnError(false);
    try (Connection connection = DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      populator.populate(connection);
    }
  }

  @BeforeEach
  void cleanTable() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("TRUNCATE TABLE dsl_runs");
    jdbcTemplate.execute("TRUNCATE TABLE dsl_run_transactions");
  }

  @Test
  void purgeRemovesOldTerminalRunsAndKeepsRunningAndYoungRuns() {
    Instant now = Instant.parse("2026-06-15T12:00:00Z");
    Instant old = now.minusSeconds(7_200);
    Instant young = now.minusSeconds(1_800);
    Instant started = now.minusSeconds(300);

    repository.save(run("run-running", DslRunStatus.RUNNING, started, null));
    repository.save(run("run-young", DslRunStatus.COMPLETED, started, young));
    for (DslRunStatus status : List.of(DslRunStatus.COMPLETED, DslRunStatus.FAILED,
            DslRunStatus.STALE, DslRunStatus.CANCELLED)) {
      repository.save(run("run-" + status.name(), status, started, old));
    }

    int deleted = repository.purgeFinishedBefore(now.minusSeconds(3_600), 2);

    assertThat(deleted).isEqualTo(4);
    assertThat(repository.findByRunId("run-running")).isPresent();
    assertThat(repository.findByRunId("run-young")).isPresent();
    for (DslRunStatus status : List.of(DslRunStatus.COMPLETED, DslRunStatus.FAILED,
            DslRunStatus.STALE, DslRunStatus.CANCELLED)) {
      assertThat(repository.findByRunId("run-" + status.name())).isEmpty();
    }
  }

  @Test
  void purgerRemovesOldRunsAndTheirTransactionsAndIncrementsCounter() {
    Instant now = Instant.parse("2026-06-15T12:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    Instant old = now.minusSeconds(7_200);
    Instant young = now.minusSeconds(1_800);
    Instant started = now.minusSeconds(300);

    repository.save(run("run-running", DslRunStatus.RUNNING, started, null));
    repository.save(run("run-young", DslRunStatus.COMPLETED, started, young));
    for (DslRunStatus status : List.of(DslRunStatus.COMPLETED, DslRunStatus.FAILED,
            DslRunStatus.STALE, DslRunStatus.CANCELLED)) {
      String runId = "run-" + status.name();
      repository.save(run(runId, status, started, old));
      transactionRepository.save(transaction(runId, "old-tx"));
    }
    transactionRepository.save(transaction("run-running", "running-tx"));
    transactionRepository.save(transaction("run-young", "young-tx"));

    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    try {
      DslRunRetentionPurger purger = new DslRunRetentionPurger(
              repository, meterRegistry, Duration.ofHours(1), Duration.ofHours(1), 2, executor,
              transactionRepository, clock);

      int deleted = purger.purge();

      assertThat(deleted).isEqualTo(4);
      assertThat(repository.findByRunId("run-running")).isPresent();
      assertThat(repository.findByRunId("run-young")).isPresent();
      for (DslRunStatus status : List.of(DslRunStatus.COMPLETED, DslRunStatus.FAILED,
              DslRunStatus.STALE, DslRunStatus.CANCELLED)) {
        String runId = "run-" + status.name();
        assertThat(repository.findByRunId(runId)).isEmpty();
        assertThat(transactionRepository.findByRunId(runId)).isEmpty();
      }
      assertThat(transactionRepository.findByRunId("run-running")).hasSize(1);
      assertThat(transactionRepository.findByRunId("run-young")).hasSize(1);

      Counter txCounter = meterRegistry.find("dsl.run.transactions.purged").counter();
      assertThat(txCounter).isNotNull();
      assertThat(txCounter.count()).isEqualTo(4.0);
    } finally {
      executor.shutdownNow();
    }
  }

  private static DslRun run(String runId, DslRunStatus status, Instant startedAt,
          Instant finishedAt) {
    return DslRun.builder()
            .runId(runId)
            .processName("retention-test")
            .status(status.name())
            .input("{}")
            .output(null)
            .error(null)
            .startedAt(startedAt)
            .finishedAt(finishedAt)
            .executionMode(ExecutionMode.RUN.name())
            .build();
  }

  private static TransactionExecution transaction(String runId, String transactionName) {
    return new TransactionExecution(runId, transactionName, null,
            Instant.parse("2026-06-15T11:00:00Z"));
  }
}
