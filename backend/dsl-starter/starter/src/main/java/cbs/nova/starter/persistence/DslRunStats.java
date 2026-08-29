package cbs.nova.starter.persistence;

import java.util.List;
import java.util.Map;

/**
 * Aggregate counters over the {@code dsl_runs} table, computed entirely in SQL.
 *
 * <p>
 * The dashboard reads these instead of counting rows client-side because the executions list
 * endpoint is paginated and clamped by {@code MAX_LIMIT}, so any client-side "total" derived from a
 * list page is wrong by construction.
 *
 * <p>
 * All counters describe whatever rows currently exist in the store. The retention purge (T276)
 * deletes finished runs older than its cutoff, so after a purge {@code totalRuns} and the
 * per-status counts shrink accordingly — the aggregates are computed over the surviving rows and no
 * assumption is made that historical rows still exist.
 *
 * @param totalRuns
 *          count of all run rows currently stored
 * @param statusCounts
 *          run count per raw stored status name (e.g. {@code RUNNING}); only statuses with at least
 *          one run appear
 * @param windowRuns
 *          runs started within the trailing window
 * @param windowFailedRuns
 *          runs started within the trailing window whose status is {@code FAILED}
 * @param windowFailureRate
 *          {@code windowFailedRuns / windowRuns}, {@code 0.0} when the window has no runs
 * @param topProcesses
 *          processes with the most stored runs, most first, capped at the requested limit
 */
public record DslRunStats(
        long totalRuns,
        Map<String, Long> statusCounts,
        long windowRuns,
        long windowFailedRuns,
        double windowFailureRate,
        List<ProcessRunCount> topProcesses) {

  public record ProcessRunCount(String processName, long runCount) {
  }
}
