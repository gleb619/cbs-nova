package cbs.nova.starter.service;

import cbs.nova.dsl.history.DslRunRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Scheduled purge of finished {@code dsl_runs} rows past a retention threshold.
 *
 * <p>
 * Mirrors the established healthcheck-sweep scheduling pattern (see
 * {@link TemporalDslProcessService}): a single dedicated {@link ScheduledExecutorService} runs the
 * purge on a fixed delay, guarded by compare-and-set startup so it is started at most once. The job
 * only ever deletes terminal rows ({@code status <> 'RUNNING'}) whose {@code finished_at} has
 * fallen before the cutoff, so it can never race a row that is mid-transition out of RUNNING.
 *
 * <p>
 * When {@code cbs.runs.retention} is {@code 0} or negative the purge is disabled: {@link #start()}
 * becomes a no-op, no job is scheduled, and nothing is registered.
 */
@Slf4j
public class DslRunRetentionPurger {

  /** Micrometer counter, incremented by the number of rows deleted per purge pass. */
  public static final String PURGED_COUNTER = "dsl.runs.purged";

  private static final Duration SHUTDOWN_JOIN = Duration.ofSeconds(5);

  private final DslRunRepository runRepository;
  private final MeterRegistry meterRegistry;
  private final Duration retention;
  private final Duration purgeInterval;
  private final int purgeBatchSize;
  private final ScheduledExecutorService schedulingExecutor;
  private final Clock clock;

  private final AtomicReference<ScheduledFuture<?>> handle = new AtomicReference<>();
  private final AtomicBoolean started = new AtomicBoolean(false);

  public DslRunRetentionPurger(
          @NonNull DslRunRepository runRepository,
          @NonNull MeterRegistry meterRegistry,
          @NonNull Duration retention,
          @NonNull Duration purgeInterval,
          int purgeBatchSize,
          @NonNull ScheduledExecutorService schedulingExecutor) {
    this(runRepository, meterRegistry, retention, purgeInterval, purgeBatchSize,
            schedulingExecutor, Clock.systemUTC());
  }

  public DslRunRetentionPurger(
          @NonNull DslRunRepository runRepository,
          @NonNull MeterRegistry meterRegistry,
          @NonNull Duration retention,
          @NonNull Duration purgeInterval,
          int purgeBatchSize,
          @NonNull ScheduledExecutorService schedulingExecutor,
          @NonNull Clock clock) {
    this.runRepository = runRepository;
    this.meterRegistry = meterRegistry;
    this.retention = retention;
    this.purgeInterval = purgeInterval;
    this.purgeBatchSize = purgeBatchSize;
    this.schedulingExecutor = schedulingExecutor;
    this.clock = clock;
  }

  /**
   * Schedules the recurring purge unless retention is disabled. Safe to call multiple times: the
   * job is scheduled at most once via a compare-and-set guard.
   */
  public void start() {
    if (retention.isZero() || retention.isNegative()) {
      log.info("dsl_runs retention purge disabled (cbs.runs.retention={}); no job scheduled",
              retention);
      return;
    }
    if (!started.compareAndSet(false, true)) {
      return;
    }
    long intervalMs = Math.max(1L, purgeInterval.toMillis());
    try {
      ScheduledFuture<?> fresh = schedulingExecutor.scheduleWithFixedDelay(
              this::purgeSafely, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
      ScheduledFuture<?> previous = handle.getAndSet(fresh);
      if (previous != null) {
        previous.cancel(false);
      }
      log.info("dsl_runs retention purge scheduled every {} (retention {}, batch size {})",
              purgeInterval, retention, purgeBatchSize);
    } catch (Exception ex) {
      started.set(false);
      handle.set(null);
      log.warn("dsl_runs retention purge could not be scheduled: {}", ex.getMessage(), ex);
    }
  }

  /** Cancels the scheduled purge if one is running. */
  public void shutdown() {
    started.set(false);
    ScheduledFuture<?> current = handle.getAndSet(null);
    if (current == null) {
      return;
    }
    current.cancel(false);
  }

  private void purgeSafely() {
    try {
      purge();
    } catch (Exception ex) {
      log.warn("dsl_runs retention purge failed: {}", ex.getMessage(), ex);
    }
  }

  /**
   * Runs a single purge pass against the current cutoff {@code now - retention}. Returns the number
   * of rows deleted, or {@code 0} when retention is disabled.
   */
  int purge() {
    if (retention.isZero() || retention.isNegative()) {
      return 0;
    }
    Instant cutoff = clock.instant().minus(retention);
    int deleted = runRepository.purgeFinishedBefore(cutoff, purgeBatchSize);
    if (deleted > 0) {
      meterRegistry.counter(PURGED_COUNTER).increment(deleted);
      log.info("Purged {} finished dsl_runs rows older than cutoff {}", deleted, cutoff);
    }
    return deleted;
  }
}
