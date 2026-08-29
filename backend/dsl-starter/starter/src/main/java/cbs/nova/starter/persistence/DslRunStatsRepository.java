package cbs.nova.starter.persistence;

import org.jspecify.annotations.NonNull;

import java.time.Instant;

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
}
