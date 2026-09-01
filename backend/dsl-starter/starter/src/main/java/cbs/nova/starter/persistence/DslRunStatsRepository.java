package cbs.nova.starter.persistence;

import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Optional capability of a {@link cbs.nova.dsl.history.DslRunRepository} implementation that can
 * compute {@link DslRunStats} as store-side aggregates.
 *
 * <p>
 * Implemented by {@link JdbcDslRunRepository} (SQL {@code COUNT}/{@code GROUP BY}). Repositories
 * that cannot aggregate server-side simply do not implement it; the executions handler then falls
 * back to scanning the repository, which is exact for in-memory stores.
 */
public interface DslRunStatsRepository {

  /**
   * Compute aggregate statistics over the currently stored runs.
   *
   * @param windowStart
   *          inclusive lower bound on {@code started_at} for the trailing-window counters
   * @param topProcessesLimit
   *          maximum number of entries in {@link DslRunStats#topProcesses()} (must be positive)
   */
  @NonNull
  DslRunStats stats(@NonNull Instant windowStart, int topProcessesLimit);

  /**
   * Compute per-bucket run counts grouped by status, ordered by bucket then status.
   *
   * <p>
   * The result mirrors the rows the SQL {@code date_trunc} bucketing yields: one
   * {@link RunTimeseriesBucket} per (bucket, status) pair that has at least one run. Empty buckets
   * are NOT emitted by the store — the handler is responsible for zero-filling so the dashboard's
   * x-axis stays uniform.
   *
   * @param windowStart
   *          inclusive lower bound on {@code started_at}
   * @param windowEnd
   *          exclusive upper bound on {@code started_at}
   * @param bucketSize
   *          bucket width; must be positive and divide evenly into {@code windowEnd - windowStart}
   *          so bucket boundaries are stable
   */
  @NonNull
  List<RunTimeseriesBucket> timeseries(@NonNull Instant windowStart, @NonNull Instant windowEnd,
          @NonNull Duration bucketSize);
}
