package cbs.nova.starter.maintenance;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.service.DslRunReconciliationService;

import java.time.Duration;

/**
 * {@link MaintenanceTask} adapter for the existing {@link DslRunReconciliationService}. Wraps the
 * service's package-private {@code reconcileOnce()} method, which performs a single stuck-run sweep
 * without re-scheduling itself.
 *
 * <p>
 * Reconciliation does not delete rows; it only writes terminal status to already-stuck RUNNING
 * rows. The {@code purged} count is therefore reported as {@code 0} — the count of resolved runs is
 * observable via the existing {@link StarterConstants#RESOLVED_COUNTER} meter.
 *
 * <p>
 * Registered under the task name {@code orphans}, which matches the
 * {@code dsl.maintenance.tasks.orphans.*} configuration key.
 */
public class DslRunReconciliationMaintenanceTask implements MaintenanceTask {

  private final DslRunReconciliationService service;

  public DslRunReconciliationMaintenanceTask(DslRunReconciliationService service) {
    this.service = service;
  }

  @Override
  public String name() {
    return StarterConstants.ORPHANS_TASK_NAME;
  }

  @Override
  public MaintenanceResult run() {
    long startNanos = System.nanoTime();
    service.reconcileOnce();
    Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
    return new MaintenanceResult(0, duration);
  }
}
