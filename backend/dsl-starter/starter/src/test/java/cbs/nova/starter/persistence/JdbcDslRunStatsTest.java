package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import java.util.Map;
import org.assertj.core.data.Offset;
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
import java.util.List;

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
            Map.entry("COMPLETED", 2L),
            Map.entry("FAILED", 1L),
            Map.entry("RUNNING", 1L),
            Map.entry("STALE", 1L));
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
    assertThat(stats.windowFailureRate()).isCloseTo(0.5, Offset.offset(1e-9));
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

  // -------------------------------------------------------------------------
  // T320 — run time-series stats. The SQL aggregates runs into per-minute
  // buckets on `started_at` and the handler folds minutes into the
  // requested bucket width; these tests pin that behavior at the JDBC layer.
  // -------------------------------------------------------------------------

  @Test
  void timeseriesOnEmptyTableReturnsEmptyList() {
    Instant now = Instant.now();
    List<RunTimeseriesBucket> rows = statsRepo().timeseries(
            now.minus(Duration.ofHours(2)),
            now,
            Duration.ofMinutes(60));

    assertThat(rows).isEmpty();
  }

  @Test
  void timeseriesBucketsMinuteRowsIntoTheRequestedWidth() {
    Instant base = Instant.parse("2026-08-13T10:00:00Z");
    saveRunAt("a-1", "Alpha", "COMPLETED", base);
    saveRunAt("a-2", "Alpha", "FAILED", base.plus(Duration.ofMinutes(20)));
    saveRunAt("a-3", "Alpha", "COMPLETED", base.plus(Duration.ofMinutes(45)));
    // The next-hour bucket has its own COMPLETED + FAILED split.
    saveRunAt("b-1", "Beta", "COMPLETED", base.plus(Duration.ofHours(1)));
    saveRunAt("b-2", "Beta", "FAILED", base.plus(Duration.ofHours(1).plus(Duration.ofMinutes(30))));

    Instant windowStart = base;
    Instant windowEnd = base.plus(Duration.ofHours(2));
    List<RunTimeseriesBucket> rows = statsRepo().timeseries(
            windowStart, windowEnd, Duration.ofMinutes(60));

    assertThat(rows).extracting(RunTimeseriesBucket::bucketStart)
            .containsExactly(windowStart, windowStart, windowStart.plus(Duration.ofHours(1)),
                    windowStart.plus(Duration.ofHours(1)));
    assertThat(rows).extracting(RunTimeseriesBucket::status)
            .containsExactly("COMPLETED", "FAILED", "COMPLETED", "FAILED");
    assertThat(rows).extracting(RunTimeseriesBucket::count)
            .containsExactly(2L, 1L, 1L, 1L);
  }

  @Test
  void timeseriesSkipsRowsOutsideTheWindowButStillCountsThemInAggregate() {
    Instant base = Instant.parse("2026-08-13T10:00:00Z");
    saveRunAt("inside-1", "Alpha", "COMPLETED", base);
    saveRunAt("inside-2", "Alpha", "FAILED", base.plus(Duration.ofMinutes(10)));
    // One minute before the window starts.
    saveRunAt("before", "Alpha", "COMPLETED", base.minus(Duration.ofMinutes(1)));
    // Exactly on the exclusive end bound — excluded.
    saveRunAt("at-end", "Alpha", "COMPLETED", base.plus(Duration.ofHours(2)));

    List<RunTimeseriesBucket> rows = statsRepo().timeseries(
            base, base.plus(Duration.ofHours(2)), Duration.ofMinutes(60));

    assertThat(rows).hasSize(2);
    assertThat(rows).extracting(RunTimeseriesBucket::bucketStart)
            .containsOnly(base);
    assertThat(rows).extracting(RunTimeseriesBucket::count)
            .containsExactlyInAnyOrder(1L, 1L);
  }

  @Test
  void timeseriesOnDifferentBucketWidthsPreservesPerStatusCounts() {
    Instant base = Instant.parse("2026-08-13T10:00:00Z");
    saveRunAt("m1", "Alpha", "COMPLETED", base.plus(Duration.ofMinutes(5)));
    saveRunAt("m2", "Alpha", "FAILED", base.plus(Duration.ofMinutes(25)));
    saveRunAt("m3", "Alpha", "COMPLETED", base.plus(Duration.ofMinutes(55)));

    // The JDBC layer emits one row per (bucket, status) that has runs; the
    // 10:30 bucket has no rows here, so it must NOT appear in the JDBC
    // response. The handler's response factory is what zero-fills missing
    // buckets so the dashboard x-axis stays uniform — see
    // ExecutionTimeseriesResponseTest.
    List<RunTimeseriesBucket> rows15 = statsRepo().timeseries(
            base, base.plus(Duration.ofHours(1)), Duration.ofMinutes(15));
    assertThat(rows15).extracting(RunTimeseriesBucket::bucketStart).containsExactly(
            base, base.plus(Duration.ofMinutes(15)),
            base.plus(Duration.ofMinutes(45)));
    assertThat(rows15).extracting(RunTimeseriesBucket::status)
            .containsExactly("COMPLETED", "FAILED", "COMPLETED");
    assertThat(rows15).extracting(RunTimeseriesBucket::count)
            .containsExactly(1L, 1L, 1L);
  }

  @Test
  void timeseriesRejectsZeroOrNegativeWindow() {
    DslRunStatsRepository repo = statsRepo();
    Instant now = Instant.now();
    assertThatThrownBy(() -> repo.timeseries(now, now, Duration.ofMinutes(60)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("windowEnd");
    assertThatThrownBy(
            () -> repo.timeseries(now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(3)),
                    Duration.ofMinutes(60)))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void timeseriesRejectsNonPositiveBucketSize() {
    DslRunStatsRepository repo = statsRepo();
    Instant now = Instant.now();
    assertThatThrownBy(() -> repo.timeseries(
            now.minus(Duration.ofHours(2)), now, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bucketSize");
    assertThatThrownBy(() -> repo.timeseries(
            now.minus(Duration.ofHours(2)), now, Duration.ofMinutes(-1)))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void timeseriesRejectsWindowThatDoesNotDivideIntoBuckets() {
    DslRunStatsRepository repo = statsRepo();
    Instant now = Instant.now();
    assertThatThrownBy(() -> repo.timeseries(
            now.minus(Duration.ofMinutes(125)), now, Duration.ofMinutes(60)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("divisible");
  }

  private void saveRunAt(String runId, String processName, String status, Instant startedAt) {
    repository.save(DslRun.builder()
            .runId(runId)
            .processName(processName)
            .status(status)
            .startedAt(startedAt)
            .finishedAt("RUNNING".equals(status) ? null : startedAt.plusSeconds(5))
            .executionMode("RUN")
            .build());
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
