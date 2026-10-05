package cbs.nova.starter.maintenance;

import cbs.nova.starter.config.properties.DslNotificationFiringRetentionProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.persistence.NotificationRuleFiringRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/**
 * Opt-in age-based retention purge for the append-only {@code dsl_notification_rule_firing} table
 * (T814).
 *
 * <p>
 * When {@link DslNotificationFiringRetentionProperties#isEnabled()} is {@code false} (the default)
 * the task is a deterministic no-op: no SQL is issued, the {@code purged} counter stays at zero,
 * and the shared {@code dsl.maintenance.task.duration} timer still records the elapsed time so a
 * disabled task remains observable.
 *
 * <p>
 * When enabled, the task computes {@code now - maxAge} and delegates to
 * {@link NotificationRuleFiringRepository#purgeOlderThan(Instant, int)} in a bounded loop until a
 * pass returns less than the configured {@code batchSize}. The single Micrometer counter
 * {@code dsl.notification.firings.purged} is incremented by the total rows removed.
 *
 * <p>
 * Registered under the task name {@code notification-firing-retention}, which matches the
 * {@code dsl.maintenance.tasks.notification-firing-retention.enabled} configuration key.
 */
@Slf4j
public class NotificationFiringRetentionMaintenanceTask implements MaintenanceTask {

  private final NotificationRuleFiringRepository repository;
  private final DslNotificationFiringRetentionProperties properties;
  private final MeterRegistry meterRegistry;
  private final Clock clock;

  /**
   * Production constructor.
   */
  public NotificationFiringRetentionMaintenanceTask(
          NotificationRuleFiringRepository repository,
          DslNotificationFiringRetentionProperties properties,
          MeterRegistry meterRegistry,
          Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.clock = clock;
  }

  @Override
  public String name() {
    return StarterConstants.NOTIFICATION_FIRING_RETENTION_TASK_NAME;
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
      log.warn("dsl_notification_rule_firing retention purge failed: {}", ex.getMessage(), ex);
      return new MaintenanceResult(0, Duration.ofNanos(System.nanoTime() - startNanos));
    }
  }

  private int purgeInBatches() {
    Duration maxAge = properties.getMaxAge();
    if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
      log.debug("notification-firing-retention: max-age is non-positive; skipping purge");
      return 0;
    }
    Instant cutoff = clock.instant().minus(maxAge);
    int batchSize = properties.getBatchSize();
    int total = 0;
    while (true) {
      int deleted = repository.purgeOlderThan(cutoff, batchSize);
      if (deleted <= 0) {
        break;
      }
      total += deleted;
      if (deleted < batchSize) {
        break;
      }
    }
    if (total > 0 && meterRegistry != null) {
      meterRegistry.counter(StarterConstants.NOTIFICATION_FIRING_RETENTION_PURGED_COUNTER)
              .increment(total);
      log.info("Purged {} dsl_notification_rule_firing rows older than cutoff {}", total, cutoff);
    }
    return total;
  }
}
