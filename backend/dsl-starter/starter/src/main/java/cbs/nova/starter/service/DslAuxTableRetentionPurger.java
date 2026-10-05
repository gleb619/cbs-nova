package cbs.nova.starter.service;

import cbs.nova.starter.config.properties.CbsRetentionProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.persistence.CompileDiagnosticCrudRepository;
import cbs.nova.starter.persistence.DslAuditCrudRepository;
import cbs.nova.starter.persistence.DslEventCrudRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Shared age-based retention purger for the append-only {@code dsl_audit}, {@code dsl_events} and
 * {@code dsl_compile_diagnostics} tables (T836).
 *
 * <p>
 * Mirrors the scheduling and metering shape of {@link DslRunRetentionPurger}: a single
 * {@link ScheduledExecutorService} drives batched
 * {@code DELETE ... WHERE <timestamp> < cutoff LIMIT batch} passes. Each table is only touched when
 * its retention is positive, and each table reports its own Micrometer counter.
 */
@Slf4j
@RequiredArgsConstructor
public class DslAuxTableRetentionPurger {

  private final DslAuditCrudRepository auditRepository;
  private final DslEventCrudRepository eventRepository;
  private final CompileDiagnosticCrudRepository diagnosticRepository;
  private final MeterRegistry meterRegistry;
  private final CbsRetentionProperties.Audit auditProperties;
  private final CbsRetentionProperties.Events eventsProperties;
  private final CbsRetentionProperties.Diagnostics diagnosticsProperties;
  private final ScheduledExecutorService schedulingExecutor;
  private final Clock clock;

  private final AtomicReference<ScheduledFuture<?>> handle = new AtomicReference<>();
  private final AtomicBoolean started = new AtomicBoolean(false);

  public void start() {
    Duration interval = enabledInterval();
    if (interval == null) {
      log.info("Aux-table retention purge disabled (cbs.{audit,events,diagnostics}.retention={}); "
              + "no job scheduled", this);
      return;
    }
    if (!started.compareAndSet(false, true)) {
      return;
    }
    long intervalMs = Math.max(1L, interval.toMillis());
    try {
      ScheduledFuture<?> fresh = schedulingExecutor.scheduleWithFixedDelay(
              this::purgeSafely, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
      ScheduledFuture<?> previous = handle.getAndSet(fresh);
      if (previous != null) {
        previous.cancel(false);
      }
      log.info("Aux-table retention purge scheduled every {} (audit {}, events {}, diagnostics {})",
              interval, auditProperties.getRetention(),
              eventsProperties.getRetention(), diagnosticsProperties.getRetention());
    } catch (Exception ex) {
      started.set(false);
      handle.set(null);
      log.warn("Aux-table retention purge could not be scheduled: {}", ex.getMessage(), ex);
    }
  }

  public void shutdown() {
    started.set(false);
    ScheduledFuture<?> current = handle.getAndSet(null);
    if (current == null) {
      return;
    }
    current.cancel(false);
  }

  public int purge() {
    Instant now = clock.instant();
    int total = 0;
    total += purgeTable("dsl_audit", auditRepository::deleteOlderThan,
            auditProperties.getRetention(), auditProperties.getPurgeBatchSize(),
            StarterConstants.AUDIT_RETENTION_PURGED_COUNTER, now);
    total += purgeTable("dsl_events", eventRepository::deleteOlderThan,
            eventsProperties.getRetention(), eventsProperties.getPurgeBatchSize(),
            StarterConstants.EVENTS_PURGED_COUNTER, now);
    total += purgeTable("dsl_compile_diagnostics", diagnosticRepository::deleteOlderThan,
            diagnosticsProperties.getRetention(), diagnosticsProperties.getPurgeBatchSize(),
            StarterConstants.DIAGNOSTICS_PURGED_COUNTER, now);
    return total;
  }

  private void purgeSafely() {
    try {
      purge();
    } catch (Exception ex) {
      log.warn("Aux-table retention purge failed: {}", ex.getMessage(), ex);
    }
  }

  private int purgeTable(String table, BiFunction<Instant, Integer, Integer> deleter,
          Duration retention, int batchSize, String counterName, Instant now) {
    if (retention == null || retention.isZero() || retention.isNegative()) {
      return 0;
    }
    if (batchSize <= 0) {
      return 0;
    }
    Instant cutoff = now.minus(retention);
    int total = 0;
    while (true) {
      int deleted = deleter.apply(cutoff, batchSize);
      if (deleted <= 0) {
        break;
      }
      total += deleted;
      if (deleted < batchSize) {
        break;
      }
    }
    if (total > 0 && meterRegistry != null) {
      meterRegistry.counter(counterName).increment(total);
      log.info("Purged {} {} rows older than cutoff {}", total, table, cutoff);
    }
    return total;
  }

  private @Nullable Duration enabledInterval() {
    Duration interval = null;
    if (isEnabled(auditProperties.getRetention())) {
      interval = min(interval, auditProperties.getPurgeInterval());
    }
    if (isEnabled(eventsProperties.getRetention())) {
      interval = min(interval, eventsProperties.getPurgeInterval());
    }
    if (isEnabled(diagnosticsProperties.getRetention())) {
      interval = min(interval, diagnosticsProperties.getPurgeInterval());
    }
    return interval;
  }

  private static boolean isEnabled(Duration retention) {
    return retention != null && !retention.isZero() && !retention.isNegative();
  }

  private static Duration min(Duration a, Duration b) {
    if (a == null) {
      return b;
    }
    if (b == null || b.isZero() || b.isNegative()) {
      return a;
    }
    return a.compareTo(b) <= 0 ? a : b;
  }

  @Override
  public String toString() {
    return String.format("audit=%s, events=%s, diagnostics=%s",
            auditProperties.getRetention(),
            eventsProperties.getRetention(),
            diagnosticsProperties.getRetention());
  }
}
