package cbs.nova.starter.service;

import cbs.nova.dsl.history.DslRunStatus;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionDescription;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowStub;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Shared Temporal status probe used by both the healthcheck stale sweep and the scheduled
 * reconciliation job. A single probe result encapsulates the status-mapping decision so the two
 * writers do not duplicate the describe/switch logic.
 */
@Slf4j
public class WorkflowStatusProbe {

  private final WorkflowClient workflowClient;

  public WorkflowStatusProbe(@NonNull WorkflowClient workflowClient) {
    this.workflowClient = workflowClient;
  }

  /**
   * Asks Temporal for the real status of the workflow whose workflow id is the given run id.
   *
   * @param runId
   *          the run id / Temporal workflow id
   * @return a probe result; never throws — Temporal errors become {@link ErrorResult}
   */
  public @NonNull ProbeResult probe(@NonNull String runId) {
    try {
      WorkflowStub stub = workflowClient.newUntypedWorkflowStub(runId);
      WorkflowExecutionDescription description = stub.describe();
      return map(description);
    } catch (WorkflowNotFoundException notFound) {
      return new NotFoundResult();
    } catch (Exception ex) {
      return new ErrorResult(ex);
    }
  }

  private static @NonNull ProbeResult map(@NonNull WorkflowExecutionDescription description) {
    WorkflowExecutionStatus temporalStatus = description.getStatus();
    Instant closeTime = description.getCloseTime();

    if (temporalStatus == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED) {
      return new TerminalResult(DslRunStatus.COMPLETED, null, closeTime);
    }
    if (temporalStatus == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_FAILED) {
      return new TerminalResult(DslRunStatus.FAILED, "Workflow failed in Temporal", closeTime);
    }
    if (temporalStatus == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TIMED_OUT) {
      return new TerminalResult(DslRunStatus.FAILED, "Workflow timed out in Temporal", closeTime);
    }
    if (temporalStatus == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_CANCELED) {
      return new TerminalResult(DslRunStatus.CANCELLED, "Workflow was cancelled in Temporal",
              closeTime);
    }
    if (temporalStatus == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TERMINATED) {
      return new TerminalResult(DslRunStatus.CANCELLED, "Workflow was terminated in Temporal",
              closeTime);
    }

    // RUNNING, CONTINUED_AS_NEW, or UNSPECIFIED: leave the row alone and let the next pass
    // re-evaluate.
    return new StillRunningResult(temporalStatus);
  }

  /** Result of a single Temporal status probe. */
  public sealed interface ProbeResult {
  }

  /** Temporal reports the workflow is still executing (RUNNING, CONTINUED_AS_NEW, UNSPECIFIED). */
  public record StillRunningResult(@Nullable WorkflowExecutionStatus temporalStatus)
          implements
            ProbeResult {
  }

  /** Temporal reports a terminal status that should be written to the dsl_runs row. */
  public record TerminalResult(
          @NonNull DslRunStatus status,
          @Nullable String error,
          @Nullable Instant closeTime) implements ProbeResult {
  }

  /** The workflow is no longer known to Temporal. */
  public record NotFoundResult() implements ProbeResult {
  }

  /** The describe call failed; callers should log and retry on the next pass. */
  public record ErrorResult(@NonNull Throwable cause) implements ProbeResult {
  }
}
