package cbs.nova.temporal;

import io.temporal.common.RetryOptions;

import java.time.Duration;

/**
 * Optional overrides for activity stub creation inside a workflow.
 *
 * <p>All fields are nullable. When a field is {@code null}, the {@link ActivityStubManager} falls
 * back to the value stored in the {@link cbs.nova.temporal.registry.ActivityRegistry}.
 *
 * <p>Because this record only contains immutable values (no system calls, no random data), passing
 * it to {@code ActivityStubManager.newActivityStub} is fully deterministic and replay-safe.
 *
 * @param startToCloseTimeout override for the activity start-to-close timeout
 * @param scheduleToCloseTimeout override for the schedule-to-close timeout
 * @param scheduleToStartTimeout override for the schedule-to-start timeout
 * @param taskQueue override for the task queue
 * @param retryOptions override for retry policy
 */
public record ActivityStubOptions(
    Duration startToCloseTimeout,
    Duration scheduleToCloseTimeout,
    Duration scheduleToStartTimeout,
    String taskQueue,
    RetryOptions retryOptions) {

  /** Returns an options instance with all fields {@code null} (i.e. use registry defaults). */
  public static ActivityStubOptions defaults() {
    return new ActivityStubOptions(null, null, null, null, null);
  }
}
