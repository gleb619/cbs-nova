package cbs.nova.starter.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;

/**
 * Self-contained fixture for the {@link WorkflowReplayGuardTest#negativeControlDetectsMismatch()}
 * negative control. The {@link CanonicalImpl} matches the recorded history captured by
 * {@link WorkflowReplayGoldensGenerator#captureReplayMismatchCanonical()}; the
 * {@link MisorderedImpl} adds an extra {@link io.temporal.workflow.Workflow#sleep} before the
 * return statement so its replay diverges from the history and Temporal reports
 * {@link io.temporal.worker.NonDeterministicException}.
 */
final class ReplayMismatchWorkflow {

  private ReplayMismatchWorkflow() {
  }

  @WorkflowInterface
  public interface ReplayMismatchWorkflowIface {
    @WorkflowMethod
    String execute();
  }

  /** Canonical impl: matches the recorded golden history. */
  public static class CanonicalImpl implements ReplayMismatchWorkflowIface {
    @Override
    public String execute() {
      return "ok";
    }
  }

  /** Deliberately reordered impl: extra sleep changes deterministic event order. */
  public static class MisorderedImpl implements ReplayMismatchWorkflowIface {
    @Override
    public String execute() {
      io.temporal.workflow.Workflow.sleep(Duration.ofMillis(1));
      return "ok";
    }
  }
}
