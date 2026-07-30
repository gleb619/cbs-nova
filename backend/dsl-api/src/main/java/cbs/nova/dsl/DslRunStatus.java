package cbs.nova.dsl;

public enum DslRunStatus {
  /** Process has been accepted and execution is in flight. */
  RUNNING,
  /** Process finished successfully with a non-null output. */
  COMPLETED,
  /** Process failed with an error. */
  FAILED,
  /**
   * Process has been running longer than the configured staleness threshold without producing a
   * final status. Transitions to {@code STALE} are triggered by the background healthcheck in
   * {@code TemporalDslProcessService}. A run can transition out of {@code STALE} again if the
   * workflow eventually completes; the {@code STALE} state is a marker, not a terminal state.
   */
  STALE
}
