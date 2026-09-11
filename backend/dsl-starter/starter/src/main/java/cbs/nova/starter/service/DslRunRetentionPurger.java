package cbs.nova.starter.service;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.starter.core.StarterConstants;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Slf4j
@RequiredArgsConstructor
public class DslRunRetentionPurger {

  private final DslRunRepository runRepository;
  private final MeterRegistry meterRegistry;
  private final Duration retention;
  private final Duration purgeInterval;
  private final int purgeBatchSize;
  private final ScheduledExecutorService schedulingExecutor;
  private final TransactionExecutionRepository transactionExecutionRepository;
  private final Clock clock;

  private final AtomicReference<ScheduledFuture<?>> handle = new AtomicReference<>();
  private final AtomicBoolean started = new AtomicBoolean(false);

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

  public int purge() {
    if (retention.isZero() || retention.isNegative()) {
      return 0;
    }
    Instant cutoff = clock.instant().minus(retention);
    int[] childDeleted = {0};
    int deleted = runRepository.purgeFinishedBefore(cutoff, purgeBatchSize, ids -> {
      if (transactionExecutionRepository != null && !ids.isEmpty()) {
        childDeleted[0] += transactionExecutionRepository.deleteByRunIds(ids);
      }
    });
    if (deleted > 0) {
      meterRegistry.counter(StarterConstants.PURGED_COUNTER).increment(deleted);
      log.info("Purged {} finished dsl_runs rows older than cutoff {}", deleted, cutoff);
    }
    if (childDeleted[0] > 0) {
      meterRegistry.counter(StarterConstants.TRANSACTIONS_PURGED_COUNTER)
              .increment(childDeleted[0]);
    }
    return deleted;
  }
}
