package cbs.nova.starter.maintenance;

import java.time.Duration;

/**
 * Outcome of one {@link MaintenanceTask#run()} invocation.
 *
 * @param purged
 *          number of items removed/resolved by the task. Tasks that don't delete rows (e.g.
 *          reconciliation) return {@code 0}.
 * @param duration
 *          wall-clock duration of the pass. Recorded into the shared
 *          {@code dsl.maintenance.task.duration} timer regardless of outcome so a slow but
 *          successful task is still observable.
 */
public record MaintenanceResult(int purged, Duration duration) {

  public static final MaintenanceResult EMPTY = new MaintenanceResult(0, Duration.ZERO);

  public MaintenanceResult {
    if (purged < 0) {
      throw new IllegalArgumentException("purged must be >= 0, was: " + purged);
    }
    if (duration == null) {
      duration = Duration.ZERO;
    }
    if (duration.isNegative()) {
      throw new IllegalArgumentException("duration must be >= 0, was: " + duration);
    }
  }
}
