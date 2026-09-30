package cbs.nova.starter.maintenance;

import cbs.nova.starter.config.properties.DslAuditRetentionProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.persistence.DslAuditStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/**
 * Opt-in age-based retention purge for the append-only {@code dsl_audit} table (T747).
 *
 * <p>
 * When {@link DslAuditRetentionProperties#isEnabled()} is {@code false} (the default) the task is a
 * deterministic no-op: no SQL is issued, the {@code purged} counter stays at zero, and the shared
 * {@code dsl.maintenance.task.duration} timer still records the elapsed time so a disabled task
 * remains observable.
 *
 * <p>
 * When enabled, the task computes {@code now - maxAgeDays} and delegates to
 * {@link DslAuditStore#purgeOlderThan(Instant, int)} in a bounded loop until a pass returns less
 * than the configured {@code batchSize}. The single Micrometer counter {@code dsl.audit.purged} is
 * incremented by the total rows removed.
 *
 * <p>
 * Registered under the task name {@code audit-retention}, which matches the
 * {@code dsl.maintenance.tasks.audit-retention.enabled} configuration key.
 */
@Slf4j
public class AuditRetentionMaintenanceTask implements MaintenanceTask {

  private final DslAuditStore auditStore;
  private final DslAuditRetentionProperties properties;
  private final MeterRegistry meterRegistry;
  private final Clock clock;

  /**
   * Production constructor.
   */
  public AuditRetentionMaintenanceTask(
          DslAuditStore auditStore,
          DslAuditRetentionProperties properties,
          MeterRegistry meterRegistry,
          Clock clock) {
    this.auditStore = auditStore;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.clock = clock;
  }

  @Override
  public String name() {
    return StarterConstants.AUDIT_RETENTION_TASK_NAME;
  }

  @Override
  public MaintenanceResult run() {
    long startNanos = System.nanoTime();
    if (!properties.isEnabled()) {
      return new MaintenanceResult(0, Duration.ofNanos(System.nanoTime() - startNanos));
    }
    try {
      int purged = purgeInBatches();
      return new MaintenanceResult(purged, Duration.ofNanos(System.nanoTime() - startNanos));
    } catch (RuntimeException ex) {
      // Surface failures to the unified driver as a zero-result; it logs the
      // failure and records the duration timer.
      log.warn("dsl_audit retention purge failed: {}", ex.getMessage(), ex);
      return new MaintenanceResult(0, Duration.ofNanos(System.nanoTime() - startNanos));
    }
  }

  private int purgeInBatches() {
    Duration maxAge = properties.getMaxAge();
    if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
      log.debug("audit-retention: max-age is non-positive; skipping purge");
      return 0;
    }
    Instant cutoff = clock.instant().minus(maxAge);
    int batchSize = properties.getBatchSize();
    int total = 0;
    while (true) {
      int deleted = auditStore.purgeOlderThan(cutoff, batchSize);
      if (deleted <= 0) {
        break;
      }
      total += deleted;
      if (deleted < batchSize) {
        break;
      }
    }
    if (total > 0 && meterRegistry != null) {
      meterRegistry.counter(StarterConstants.AUDIT_RETENTION_PURGED_COUNTER).increment(total);
      log.info("Purged {} dsl_audit rows older than cutoff {}", total, cutoff);
    }
    return total;
  }
}
