package cbs.nova.starter.maintenance;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.service.DslRunRetentionPurger;

import java.time.Duration;

/**
 * {@link MaintenanceTask} adapter for the existing {@link DslRunRetentionPurger}. Wraps the
 * purger's public {@code purge()} method; does not duplicate or replace it — keeping the purger's
 * tests green is a hard requirement of T412.
 *
 * <p>
 * Registered under the task name {@code run-retention}, which matches the
 * {@code dsl.maintenance.tasks.run-retention.*} configuration key.
 */
public class DslRunRetentionMaintenanceTask implements MaintenanceTask {

  private final DslRunRetentionPurger purger;

  public DslRunRetentionMaintenanceTask(DslRunRetentionPurger purger) {
    this.purger = purger;
  }

  @Override
  public String name() {
    return StarterConstants.RUN_RETENTION_TASK_NAME;
  }

  @Override
  public MaintenanceResult run() {
    long startNanos = System.nanoTime();
    int purged = purger.purge();
    Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
    return new MaintenanceResult(purged, duration);
  }
}
