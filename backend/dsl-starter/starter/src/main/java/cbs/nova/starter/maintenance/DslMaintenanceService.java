package cbs.nova.starter.maintenance;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single scheduled driver for all {@link MaintenanceTask} beans in the application context.
 *
 * <p>
 * Iterates the registered tasks in deterministic order (sorted by {@link MaintenanceTask#name()}),
 * runs each task inside its own try/catch so one failing task does not block the others, and
 * records shared Micrometer meters:
 * <ul>
 * <li>{@code dsl.maintenance.task.duration} — Timer, tagged {@code task=<name>}.</li>
 * <li>{@code dsl.maintenance.task.purged} — Counter, tagged {@code task=<name>}.</li>
 * </ul>
 * The {@link MeterRegistry} is optional: when absent, the driver still runs tasks normally but no
 * metrics are recorded (matching the T348 idiom where Micrometer is {@code compileOnly} on the
 * starter).
 *
 * <p>
 * Tasks absent from {@link #taskEnabled} default to enabled — i.e. if a task is registered but the
 * operator hasn't set {@code dsl.maintenance.tasks.<name>.enabled}, it runs.
 */
@Slf4j
public class DslMaintenanceService {

  /** Micrometer meter name for per-task wall-clock duration. */
  public static final String TASK_DURATION_TIMER = "dsl.maintenance.task.duration";

  /** Micrometer meter name for per-task purged-count. */
  public static final String TASK_PURGED_COUNTER = "dsl.maintenance.task.purged";

  /** Micrometer tag key identifying the task. */
  public static final String TASK_TAG = "task";

  private final List<MaintenanceTask> tasks;
  private final Map<String, Boolean> taskEnabled;
  private final Duration scheduleInterval;
  private final ScheduledExecutorService executor;
  private final @Nullable MeterRegistry meterRegistry;

  private final AtomicReference<ScheduledFuture<?>> handle = new AtomicReference<>();
  private final AtomicBoolean started = new AtomicBoolean(false);

  public DslMaintenanceService(
          @NonNull List<MaintenanceTask> tasks,
          @NonNull Map<String, Boolean> taskEnabled,
          @NonNull Duration scheduleInterval,
          @NonNull ScheduledExecutorService executor) {
    this(tasks, taskEnabled, scheduleInterval, executor, null);
  }

  public DslMaintenanceService(
          @NonNull List<MaintenanceTask> tasks,
          @NonNull Map<String, Boolean> taskEnabled,
          @NonNull Duration scheduleInterval,
          @NonNull ScheduledExecutorService executor,
          @Nullable MeterRegistry meterRegistry) {
    // Deterministic ordering: sort by task name so logs and meter tag order
    // are stable regardless of bean-registration order.
    this.tasks = tasks.stream()
            .sorted(Comparator.comparing(MaintenanceTask::name))
            .toList();
    this.taskEnabled = taskEnabled;
    this.scheduleInterval = scheduleInterval;
    this.executor = executor;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Returns an immutable, sorted view of the registered tasks. Used by tests and by the
   * configuration layer when reconciling legacy settings.
   */
  public List<MaintenanceTask> tasks() {
    return tasks;
  }

  /** Whether a given task is enabled under the unified driver. */
  public boolean isEnabled(String taskName) {
    Boolean explicit = taskEnabled.get(taskName);
    return explicit == null ? true : explicit;
  }

  /**
   * Schedule the unified maintenance pass. Idempotent — a second {@code start()} is a no-op.
   * Exposed as a manual start (matching the pre-existing per-job services) so the surrounding
   * configuration can wire it to an {@link org.springframework.boot.ApplicationRunner}.
   */
  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }
    long intervalMs = Math.max(1L, scheduleInterval.toMillis());
    try {
      ScheduledFuture<?> fresh = executor.scheduleWithFixedDelay(
              this::runSafely, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
      ScheduledFuture<?> previous = handle.getAndSet(fresh);
      if (previous != null) {
        previous.cancel(false);
      }
      log.info("dsl.maintenance unified scheduler started every {} (tasks: {})",
              scheduleInterval, tasks.stream().map(MaintenanceTask::name).toList());
    } catch (Exception ex) {
      started.set(false);
      handle.set(null);
      log.warn("dsl.maintenance unified scheduler could not be started: {}", ex.getMessage(), ex);
    }
  }

  /** Stop the unified scheduler. Idempotent. */
  public void shutdown() {
    started.set(false);
    ScheduledFuture<?> current = handle.getAndSet(null);
    if (current != null) {
      current.cancel(false);
    }
  }

  /** Run all enabled tasks once. Public so tests can drive the service directly. */
  public void runOnce() {
    runSafely();
  }

  private void runSafely() {
    try {
      for (MaintenanceTask task : tasks) {
        runOne(task);
      }
    } catch (Exception unexpected) {
      // Defensive: an exception that escapes runOne itself (should not
      // happen — runOne catches Throwable — but kept for safety) does not
      // propagate up to the ScheduledExecutorService.
      log.warn("dsl.maintenance unified pass failed: {}", unexpected.getMessage(), unexpected);
    }
  }

  private void runOne(MaintenanceTask task) {
    if (!isEnabled(task.name())) {
      log.debug("dsl.maintenance task '{}' disabled — skipping", task.name());
      return;
    }
    long startNanos = System.nanoTime();
    MaintenanceResult result;
    try {
      result = task.run();
    } catch (Throwable failure) {
      long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
      recordDuration(task.name(), Duration.ofMillis(durationMs));
      log.warn("dsl.maintenance task '{}' failed after {}ms: {}",
              task.name(), durationMs, failure.getMessage(), failure);
      return;
    }
    if (result == null) {
      result = MaintenanceResult.EMPTY;
    }
    recordDuration(task.name(), result.duration());
    recordPurged(task.name(), result.purged());
    if (result.purged() > 0) {
      log.info("dsl.maintenance task '{}' purged {} items in {}",
              task.name(), result.purged(), result.duration());
    } else if (log.isDebugEnabled()) {
      log.debug("dsl.maintenance task '{}' ran (0 items) in {}",
              task.name(), result.duration());
    }
  }

  private void recordDuration(String taskName, Duration duration) {
    if (meterRegistry == null) {
      return;
    }
    Timer.builder(TASK_DURATION_TIMER)
            .tag(TASK_TAG, taskName)
            .register(meterRegistry)
            .record(duration);
  }

  private void recordPurged(String taskName, int purged) {
    if (meterRegistry == null || purged <= 0) {
      return;
    }
    // Mirror the existing purge-counter idiom (Counter rather than
    // DistributionSummary) so dashboards that already group by counter see
    // the unified metric without surprise. Micrometer dedupes by (name, tags).
    Counter.builder(TASK_PURGED_COUNTER)
            .tag(TASK_TAG, taskName)
            .register(meterRegistry)
            .increment(purged);
  }
}
