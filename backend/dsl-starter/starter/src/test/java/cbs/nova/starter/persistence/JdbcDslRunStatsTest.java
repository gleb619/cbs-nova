package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;

/**
 * Verifies the SQL aggregate statements behind {@code GET /api/executions/stats} against a real
 * database (H2 in PostgreSQL mode — the same engine the unit-test datasource uses, so the
 * {@code COUNT(*) FILTER} window syntax is exercised). The Postgres-flavoured end-to-end variant
 * lives in {@code DslRunStatsIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "dsl.worker.enabled=false",
    "spring.flyway.enabled=true"
})
class JdbcDslRunStatsTest {

  @Autowired
  private DslRunRepository repository;

  @Autowired
  private DataSource dataSource;

  @BeforeEach
  void cleanTable() {
    new JdbcTemplate(dataSource).execute("TRUNCATE TABLE dsl_runs");
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

  @Test
  void statsCountsEveryStoredRunPerStatus() {
    saveRun("run-1", "LoanDisbursement", "COMPLETED", Duration.ofHours(1));
    saveRun("run-2", "LoanDisbursement", "FAILED", Duration.ofHours(2));
    saveRun("run-3", "CreditScoring", "RUNNING", Duration.ofMinutes(5));
    saveRun("run-4", "CreditScoring", "COMPLETED", Duration.ofHours(30));
    saveRun("run-5", "Payments", "STALE", Duration.ofHours(40));

    DslRunStats stats = statsRepo().stats(Instant.now().minus(Duration.ofHours(24)), 5);

    assertThat(stats.totalRuns()).isEqualTo(5);
    assertThat(stats.statusCounts()).containsOnly(
            java.util.Map.entry("COMPLETED", 2L),
            java.util.Map.entry("FAILED", 1L),
            java.util.Map.entry("RUNNING", 1L),
            java.util.Map.entry("STALE", 1L));
  }

  @Test
  void statsTrailingWindowOnlyCountsRecentRunsAndFailedShare() {
    Instant now = Instant.now();
    saveRun("recent-ok", "LoanDisbursement", "COMPLETED", Duration.ofHours(1));
    saveRun("recent-fail-1", "LoanDisbursement", "FAILED", Duration.ofHours(2));
    saveRun("recent-fail-2", "LoanDisbursement", "FAILED", Duration.ofHours(3));
    saveRun("recent-running", "LoanDisbursement", "RUNNING", Duration.ZERO);
    // Outside the 24h window: excluded from window counters, still in totals.
    saveRun("old-fail", "LoanDisbursement", "FAILED", Duration.ofHours(30));

    DslRunStats stats = statsRepo().stats(now.minus(Duration.ofHours(24)), 5);

    assertThat(stats.totalRuns()).isEqualTo(5);
    assertThat(stats.windowRuns()).isEqualTo(4);
    assertThat(stats.windowFailedRuns()).isEqualTo(2);
    assertThat(stats.windowFailureRate()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  void statsWindowFailureRateIsZeroWhenWindowHasNoRuns() {
    saveRun("old-run", "LoanDisbursement", "FAILED", Duration.ofHours(48));

    DslRunStats stats = statsRepo().stats(Instant.now().minus(Duration.ofHours(24)), 5);

    assertThat(stats.windowRuns()).isZero();
    assertThat(stats.windowFailureRate()).isZero();
  }

  @Test
  void statsTopProcessesAreOrderedByRunCountThenNameAndCapped() {
    saveRun("a-1", "Alpha", "COMPLETED", Duration.ofHours(1));
    saveRun("b-1", "Beta", "COMPLETED", Duration.ofHours(1));
    saveRun("b-2", "Beta", "COMPLETED", Duration.ofHours(1));
    saveRun("g-1", "Gamma", "COMPLETED", Duration.ofHours(1));
    saveRun("g-2", "Gamma", "COMPLETED", Duration.ofHours(1));

    DslRunStats stats = statsRepo().stats(Instant.now().minus(Duration.ofHours(24)), 2);

    assertThat(stats.topProcesses()).hasSize(2);
    // Beta and Gamma tie at 2 runs; name breaks the tie alphabetically.
    assertThat(stats.topProcesses())
            .extracting(DslRunStats.ProcessRunCount::processName)
            .containsExactly("Beta", "Gamma");
    assertThat(stats.topProcesses())
            .extracting(DslRunStats.ProcessRunCount::runCount)
            .containsExactly(2L, 2L);
  }

  @Test
  void statsRejectsNonPositiveTopProcessesLimit() {
    DslRunStatsRepository statsRepo = statsRepo();
    assertThatThrownBy(() -> statsRepo.stats(Instant.now(), 0))
            .isInstanceOf(IllegalArgumentException.class);
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

  @SpringBootApplication(scanBasePackages = "cbs.nova.starter")
  static class TestApplication {
    public static void main(String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }
}
