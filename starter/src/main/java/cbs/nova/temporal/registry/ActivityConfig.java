package cbs.nova.temporal.registry;

import io.temporal.common.RetryOptions;

import java.time.Duration;

/**
 * Configuration record for a Temporal activity, resolved from an {@link ActivityRegistry}.
 *
 * <p>All values are immutable and set at worker startup time. The {@link ActivityStubManager} reads
 * these values deterministically during workflow replay, ensuring no non-deterministic behaviour.
 *
 * @param logicalName the logical name used to look up this activity (e.g. "ProcessInput")
 * @param activityInterface the activity interface class
 * @param taskQueue the Temporal task queue this activity is registered on
 * @param startToCloseTimeout max time from first activity execution attempt to completion
 * @param scheduleToCloseTimeout max time from first schedule to completion (including retries)
 * @param scheduleToStartTimeout max time to wait for a worker to pick up the activity
 * @param retryOptions optional retry policy; {@code null} means use Temporal defaults
 */
public record ActivityConfig(
    String logicalName,
    Class<?> activityInterface,
    String taskQueue,
    Duration startToCloseTimeout,
    Duration scheduleToCloseTimeout,
    Duration scheduleToStartTimeout,
    RetryOptions retryOptions) {}
