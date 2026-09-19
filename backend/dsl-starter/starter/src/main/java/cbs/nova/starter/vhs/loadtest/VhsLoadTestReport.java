package cbs.nova.starter.vhs.loadtest;

import cbs.nova.starter.vhs.replay.VhsReplayReport;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * Aggregated report from a VHS load test run. Contains per-tape/process percentile summaries and
 * overall statistics.
 *
 * @param target
 *          the replay target used
 * @param tapesLoaded
 *          number of tape files resolved from the glob
 * @param totalCalls
 *          total calls executed across all tapes/copies
 * @param successfulCalls
 *          calls that completed successfully
 * @param failedCalls
 *          calls that failed
 * @param wallClockMs
 *          total wall-clock time of the load test
 * @param tapeSummaries
 *          per-tape aggregate summaries with percentile data
 * @param rawReport
 *          the underlying VhsReplayReport from each tape replay
 */
public record VhsLoadTestReport(
        String target,
        int tapesLoaded,
        long totalCalls,
        long successfulCalls,
        long failedCalls,
        long wallClockMs,
        List<TapePercentileSummary> tapeSummaries,
        List<VhsReplayReport> rawReports) {

  /**
   * Per-tape aggregate with percentile latencies.
   */
  public record TapePercentileSummary(
          String tapeName,
          long totalCalls,
          long successfulCalls,
          long failedCalls,
          long p50Ms,
          long p95Ms,
          long p99Ms,
          double errorRatePct) {
  }
}
