package cbs.nova.starter.vhs.replay;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Immutable report of a replay run.
 *
 * @param mode
 *          replay mode used
 * @param tapeCount
 *          number of tape copies executed
 * @param totalCalls
 *          number of recorded calls attempted
 * @param successfulCalls
 *          calls completed successfully
 * @param failedCalls
 *          calls that failed (a failing call aborts that tape's replay)
 * @param skippedCopies
 *          load-mode copies not started because the global duration cap was reached
 * @param wallClockMs
 *          total replay wall-clock time
 * @param observations
 *          per-call latency observations in execution order (across tapes, load mode is
 *          non-deterministic across copies)
 * @param tapes
 *          per-tape summaries
 */
public record VhsReplayReport(
        ReplayMode mode,
        int tapeCount,
        long totalCalls,
        long successfulCalls,
        long failedCalls,
        int skippedCopies,
        long wallClockMs,
        List<CallObservation> observations,
        List<TapeSummary> tapes) {

  public VhsReplayReport {
    observations = List.copyOf(observations);
    tapes = List.copyOf(tapes);
  }

  /**
   * Per-call observation: what was invoked, how long it took, whether it succeeded, and (when
   * output comparison is enabled) whether the replayed output matched the recorded one.
   */
  public record CallObservation(
          String tapeId,
          String callId,
          String type,
          String target,
          String operation,
          long latencyMs,
          boolean success,
          @Nullable String error,
          boolean outputCompared,
          boolean outputMismatch) {
  }

  /**
   * Per-tape aggregate.
   */
  public record TapeSummary(
          String tapeId,
          long totalCalls,
          long successfulCalls,
          long failedCalls,
          long durationMs) {

    public boolean isSuccess() {
      return failedCalls == 0;
    }
  }
}
