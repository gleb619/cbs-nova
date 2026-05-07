package cbs.nova.temporal;

import cbs.nova.temporal.registry.ActivityConfig;
import cbs.nova.temporal.registry.ActivityRegistry;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * Deterministic manager for creating Temporal activity stubs inside workflows.
 *
 * <p><strong>Determinism guarantee:</strong> This class holds a static reference to an
 * {@link ActivityRegistry} that is injected once at worker startup (before any workflow replays).
 * The registry itself is immutable: it contains only pre-configured values (task queues, timeouts,
 * interface classes). No system calls, no random values, and no external I/O are performed during
 * stub creation. Therefore every workflow replay produces exactly the same {@link ActivityOptions},
 * and Temporal considers the workflow deterministic.
 *
 * <p>Usage inside a workflow implementation:
 *
 * <pre>
 *   MyActivity stub = ActivityStubManager.newActivityStub(
 *       MyActivity.class, "MyLogicalActivity", ActivityStubOptions.defaults());
 *   stub.doWork(input);
 * </pre>
 *
 * @see Workflow#newActivityStub(Class, ActivityOptions)
 */
public final class ActivityStubManager {

  private static volatile ActivityRegistry registry;

  private ActivityStubManager() {}

  /**
   * Initialises the manager with the given registry.
   *
   * <p>This method must be called exactly once, before any worker starts processing workflows.
   * Spring Boot auto-configuration performs this automatically via
   * {@link cbs.nova.config.TemporalManagerAutoConfiguration}.
   *
   * @param activityRegistry the immutable activity registry
   * @throws IllegalStateException if already initialised
   */
  public static synchronized void initialize(ActivityRegistry activityRegistry) {
    if (registry != null) {
      throw new IllegalStateException("ActivityStubManager is already initialised");
    }
    registry = activityRegistry;
  }

  /**
   * Creates a typed activity stub by looking up the logical name in the registry and applying any
   * runtime overrides.
   *
   * <p>This method is safe to call inside a replaying workflow because it only reads from the
   * static, immutable registry and builds {@link ActivityOptions} from deterministic values.
   *
   * @param <T> the activity interface type
   * @param activityInterface the activity interface class
   * @param logicalActivityName the logical name used for registry lookup
   * @param overrides optional overrides; use {@link ActivityStubOptions#defaults()} for none
   * @return a typed activity stub ready for invocation
   * @throws IllegalStateException if the manager has not been initialised
   * @throws IllegalArgumentException if the logical name is not registered
   */
  public static <T> T newActivityStub(
      Class<T> activityInterface, String logicalActivityName, ActivityStubOptions overrides) {

    if (registry == null) {
      throw new IllegalStateException(
          "ActivityStubManager has not been initialised. Ensure the registry is configured before workers start.");
    }

    ActivityConfig config = registry.getActivityConfig(logicalActivityName);

    ActivityOptions.Builder builder = ActivityOptions.newBuilder()
        .setTaskQueue(resolveTaskQueue(config, overrides))
        .setStartToCloseTimeout(resolveStartToCloseTimeout(config, overrides));

    Duration scheduleToClose = resolveScheduleToCloseTimeout(config, overrides);
    if (scheduleToClose != null) {
      builder.setScheduleToCloseTimeout(scheduleToClose);
    }

    Duration scheduleToStart = resolveScheduleToStartTimeout(config, overrides);
    if (scheduleToStart != null) {
      builder.setScheduleToStartTimeout(scheduleToStart);
    }

    if (overrides != null && overrides.retryOptions() != null) {
      builder.setRetryOptions(overrides.retryOptions());
    } else if (config.retryOptions() != null) {
      builder.setRetryOptions(config.retryOptions());
    }

    return Workflow.newActivityStub(activityInterface, builder.build());
  }

  private static String resolveTaskQueue(ActivityConfig config, ActivityStubOptions overrides) {
    if (overrides != null && overrides.taskQueue() != null) {
      return overrides.taskQueue();
    }
    return config.taskQueue();
  }

  private static Duration resolveStartToCloseTimeout(
      ActivityConfig config, ActivityStubOptions overrides) {
    if (overrides != null && overrides.startToCloseTimeout() != null) {
      return overrides.startToCloseTimeout();
    }
    return config.startToCloseTimeout();
  }

  private static Duration resolveScheduleToCloseTimeout(
      ActivityConfig config, ActivityStubOptions overrides) {
    if (overrides != null && overrides.scheduleToCloseTimeout() != null) {
      return overrides.scheduleToCloseTimeout();
    }
    return config.scheduleToCloseTimeout();
  }

  private static Duration resolveScheduleToStartTimeout(
      ActivityConfig config, ActivityStubOptions overrides) {
    if (overrides != null && overrides.scheduleToStartTimeout() != null) {
      return overrides.scheduleToStartTimeout();
    }
    return config.scheduleToStartTimeout();
  }
}
