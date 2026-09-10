package cbs.nova.starter.maintenance;

/**
 * A single unit of recurring background work folded into the unified {@link DslMaintenanceService}.
 *
 * <p>
 * Each task wraps an existing purge/sweep/reconcile logic; the unified driver invokes every enabled
 * task in a deterministic order, guards failures so one broken task cannot stop the others, and
 * records shared Micrometer meters tagged with {@link #name()}.
 *
 * <p>
 * Implementations are expected to be idempotent and side-effecting only on their own data — the
 * same scheduling tick may run multiple tasks in sequence.
 */
public interface MaintenanceTask {

  /**
   * Stable identifier used as the {@code task} tag on the shared meters and for per-task
   * configuration under {@code dsl.maintenance.tasks.<name>.*}. Must be unique across all
   * registered tasks and contain only characters safe for both a Micrometer tag value and a Spring
   * property key segment (lowercase letters, digits, dot, hyphen, underscore).
   */
  String name();

  /**
   * Run one maintenance pass.
   *
   * @return a {@link MaintenanceResult} carrying the number of items purged (or {@code 0} when the
   *         task is a non-purge sweep such as reconciliation) and the wall-clock duration of the
   *         pass. Returning a {@code null} result is treated as an empty success ({@code purged=0},
   *         {@code duration=0}).
   */
  MaintenanceResult run();
}
