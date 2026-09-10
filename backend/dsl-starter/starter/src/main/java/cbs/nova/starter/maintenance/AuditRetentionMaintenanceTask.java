package cbs.nova.starter.maintenance;

/**
 * Forward-compat stub for audit-log retention.
 *
 * <p>
 * Real audit retention will land once T383-derived retention requirements exist (per the T412
 * plan). Until then this task is a deterministic no-op so operators can already wire
 * {@code dsl.maintenance.audit-retention.*} keys and have them honoured by the unified driver.
 *
 * <p>
 * The no-op keeps a {@link MaintenanceResult} with {@code purged=0} and the elapsed wall-clock
 * duration so the shared duration timer still records each invocation (cheap, but visible in
 * metrics).
 */
public class AuditRetentionMaintenanceTask implements MaintenanceTask {

  /** Stable task name used as the {@code task} Micrometer tag. */
  public static final String NAME = "audit-retention";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public MaintenanceResult run() {
    return MaintenanceResult.EMPTY;
  }
}
