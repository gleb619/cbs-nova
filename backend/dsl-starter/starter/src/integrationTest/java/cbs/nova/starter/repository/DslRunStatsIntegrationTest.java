package cbs.nova.starter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.starter.IntegrationTestApplication;
import cbs.nova.starter.persistence.DslRunStats;
import cbs.nova.starter.persistence.DslRunStatsRepository;
import java.util.Map;
import org.assertj.core.data.Offset;
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
import java.time.Duration;
import java.time.Instant;

/**
 * Postgres-backed verification of the SQL aggregates behind {@code GET /api/executions/stats}:
 * mixed-status seeds, the trailing-24h window, top-process ordering, and the interplay with the
 * retention purge (T276) — aggregates must describe exactly the rows that survive a purge, never
 * assume historical rows exist.
 */
@Testcontainers
@SpringBootTest(classes = IntegrationTestApplication.class, properties = {
    "dsl.worker.enabled=false"})
class DslRunStatsIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

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
  private DataSource dataSource;

  @BeforeAll
  static void applyMigrations() throws SQLException {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V1__create_dsl_runs.sql"),
            new ClassPathResource("db/migration/V2__add_context_json.sql"),
            new ClassPathResource("db/migration/V3__create_dsl_run_transactions.sql"),
            new ClassPathResource("db/migration/V4__dsl_runs_indexes.sql"),
            new ClassPathResource("db/migration/V5__dsl_runs_triggered_by.sql"));
    populator.setContinueOnError(false);
    try (Connection connection = DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      populator.populate(connection);
    }
  }

  @BeforeEach
  void cleanTable() {
    new JdbcTemplate(dataSource).execute("TRUNCATE TABLE dsl_runs");
  }

  @Test
  void statsAggregatesMixedStatusRowsOverRealPostgres() {
    saveRun("recent-ok", "LoanDisbursement", "COMPLETED", Duration.ofHours(1));
    saveRun("recent-fail", "LoanDisbursement", "FAILED", Duration.ofHours(2));
    saveRun("recent-running", "CreditScoring", "RUNNING", Duration.ofMinutes(10));
    saveRun("recent-stale", "CreditScoring", "STALE", Duration.ofHours(3));
    saveRun("old-ok", "Payments", "COMPLETED", Duration.ofHours(30));
    saveRun("old-fail", "Payments", "FAILED", Duration.ofHours(40));

    DslRunStats stats = statsRepo().stats(Instant.now().minus(Duration.ofHours(24)), 5);

    assertThat(stats.totalRuns()).isEqualTo(6);
    assertThat(stats.statusCounts()).containsOnly(
            Map.entry("COMPLETED", 2L),
            Map.entry("FAILED", 2L),
            Map.entry("RUNNING", 1L),
            Map.entry("STALE", 1L));
    assertThat(stats.windowRuns()).isEqualTo(4);
    assertThat(stats.windowFailedRuns()).isEqualTo(1);
    assertThat(stats.windowFailureRate()).isCloseTo(0.25,
            Offset.offset(1e-9));
    assertThat(stats.topProcesses())
            .extracting(DslRunStats.ProcessRunCount::processName)
            .containsExactly("CreditScoring", "LoanDisbursement", "Payments");
    assertThat(stats.topProcesses())
            .extracting(DslRunStats.ProcessRunCount::runCount)
            .containsExactly(2L, 2L, 2L);
  }

  @Test
  void statsDescribesOnlyRowsThatSurviveARetentionPurge() {
    Instant now = Instant.now();
    saveRun("keep-recent", "LoanDisbursement", "FAILED", Duration.ofHours(1));
    saveRun("purge-old-ok", "Payments", "COMPLETED", Duration.ofHours(48));
    saveRun("purge-old-fail", "Payments", "FAILED", Duration.ofHours(72));
    // RUNNING rows are never purge-eligible even when old.
    saveRun("keep-running", "Payments", "RUNNING", Duration.ofHours(96));

    int purged = repository.purgeFinishedBefore(now.minus(Duration.ofHours(24)), 100);
    assertThat(purged).isEqualTo(2);

    DslRunStats stats = statsRepo().stats(now.minus(Duration.ofHours(24)), 5);

    // Aggregates shrink to the surviving rows; no assumption that history still exists.
    assertThat(stats.totalRuns()).isEqualTo(2);
    assertThat(stats.statusCounts()).containsOnly(
            Map.entry("FAILED", 1L),
            Map.entry("RUNNING", 1L));
    assertThat(stats.windowRuns()).isEqualTo(1);
    assertThat(stats.windowFailedRuns()).isEqualTo(1);
    assertThat(stats.windowFailureRate()).isCloseTo(1.0,
            Offset.offset(1e-9));
    assertThat(stats.topProcesses()).hasSize(2);
  }

  @Test
  void statsOnEmptyTableReturnsZeroedShape() {
    DslRunStats stats = statsRepo().stats(Instant.now().minus(Duration.ofHours(24)), 5);

    assertThat(stats.totalRuns()).isZero();
    assertThat(stats.statusCounts()).isEmpty();
    assertThat(stats.windowRuns()).isZero();
    assertThat(stats.windowFailedRuns()).isZero();
    assertThat(stats.windowFailureRate()).isZero();
    assertThat(stats.topProcesses()).isEmpty();
  }

  private DslRunStatsRepository statsRepo() {
    assertThat(repository).isInstanceOf(DslRunStatsRepository.class);
    return (DslRunStatsRepository) repository;
  }

  private void saveRun(String runId, String processName, String status, Duration startedAgo) {
    Instant startedAt = Instant.now().minus(startedAgo);
    repository.save(DslRun.builder()
            .runId(runId)
            .processName(processName)
            .status(status)
            .startedAt(startedAt)
            .finishedAt("RUNNING".equals(status) ? null : startedAt.plusSeconds(5))
            .executionMode("RUN")
            .build());
  }
}
