package cbs.nova.starter.service;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionDescription;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowStub;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Scheduled reconciliation that asks Temporal for the real outcome of stuck {@code RUNNING}
 * {@code dsl_runs} rows and writes the correct terminal status.
 *
 * <p>
 * This service complements, but does not replace, the existing STALE sweep in
 * {@link TemporalDslProcessService}: when Temporal cannot answer (for example because the workflow
 * was already reaped) the row is still eventually marked {@link DslRunStatus#STALE} by the
 * healthcheck sweep.
 */
@Slf4j
public class DslRunReconciliationService {

  public static final String INSPECTED_COUNTER = "dsl.run.reconciliation.inspected";
  public static final String RESOLVED_COUNTER = "dsl.run.reconciliation.resolved";

  static final String PROCESS_NAME_TAG = "processName";
  static final String STATUS_TAG = "status";
  static final String EMPTY_OUTPUT_JSON = "{}";

  private static final Duration SHUTDOWN_JOIN = Duration.ofSeconds(5);
  private static final String UNKNOWN_PROCESS = "unknown";

  private final DslRunRepository runRepository;
  private final WorkflowClient workflowClient;
  private final MeterRegistry meterRegistry;
  private final Duration scanInterval;
  private final Duration gracePeriod;
  private final int batchSize;
  private final ScheduledExecutorService schedulingExecutor;
  private final Clock clock;

  private final AtomicReference<ScheduledFuture<?>> handle = new AtomicReference<>();
  private final AtomicBoolean started = new AtomicBoolean(false);

  public DslRunReconciliationService(
          @NonNull DslRunRepository runRepository,
          @NonNull WorkflowClient workflowClient,
          @NonNull MeterRegistry meterRegistry,
          @NonNull Duration scanInterval,
          @NonNull Duration gracePeriod,
          int batchSize,
          @NonNull ScheduledExecutorService schedulingExecutor) {
    this(runRepository, workflowClient, meterRegistry, scanInterval, gracePeriod, batchSize,
            schedulingExecutor, Clock.systemUTC());
  }

  DslRunReconciliationService(
          @NonNull DslRunRepository runRepository,
          @NonNull WorkflowClient workflowClient,
          @NonNull MeterRegistry meterRegistry,
          @NonNull Duration scanInterval,
          @NonNull Duration gracePeriod,
          int batchSize,
          @NonNull ScheduledExecutorService schedulingExecutor,
          @NonNull Clock clock) {
    this.runRepository = runRepository;
    this.workflowClient = workflowClient;
    this.meterRegistry = meterRegistry;
    this.scanInterval = scanInterval;
    this.gracePeriod = gracePeriod;
    this.batchSize = batchSize;
    this.schedulingExecutor = schedulingExecutor;
    this.clock = clock;
  }

  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }
    long intervalMs = Math.max(1L, scanInterval.toMillis());
    try {
      ScheduledFuture<?> fresh = schedulingExecutor.scheduleWithFixedDelay(
              this::reconcileSafely, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
      ScheduledFuture<?> previous = handle.getAndSet(fresh);
      if (previous != null) {
        previous.cancel(false);
      }
      log.info("dsl_runs stuck-run reconciliation scheduled every {} (grace {}, batch size {})",
              scanInterval, gracePeriod, batchSize);
    } catch (Exception ex) {
      started.set(false);
      handle.set(null);
      log.warn("dsl_runs stuck-run reconciliation could not be scheduled: {}", ex.getMessage(), ex);
    }
  }

  public void stop() {
    started.set(false);
    ScheduledFuture<?> current = handle.getAndSet(null);
    if (current == null) {
      return;
    }
    current.cancel(false);
    try {
      current.get(SHUTDOWN_JOIN.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (Exception expected) {
      // shutdown best-effort
    }
  }

  void reconcileSafely() {
    try {
      reconcileOnce();
    } catch (Exception ex) {
      log.warn("dsl_runs stuck-run reconciliation pass failed: {}", ex.getMessage(), ex);
    }
  }

  void reconcileOnce() {
    Instant cutoff = clock.instant().minus(gracePeriod);
    runRepository.knownProcessNames().stream()
            .flatMap(processName -> runRepository.findByProcessName(processName).stream())
            .filter(run -> DslRunStatus.RUNNING.name().equals(run.status()))
            .filter(run -> run.startedAt().isBefore(cutoff))
            .limit(batchSize)
            .forEach(this::reconcileRun);
  }

  private void reconcileRun(@NonNull DslRun run) {
    String runId = run.runId();
    meterRegistry.counter(INSPECTED_COUNTER,
            PROCESS_NAME_TAG, safeProcessName(run.processName())).increment();
    try {
      WorkflowStub stub = workflowClient.newUntypedWorkflowStub(runId);
      WorkflowExecutionDescription description = stub.describe();
      resolveIfTerminal(run, description);
    } catch (WorkflowNotFoundException notFound) {
      log.warn("No Temporal workflow found for run {}; marking STALE", runId);
      writeTerminal(run, DslRunStatus.STALE, "Workflow not found in Temporal; run is stale", null);
    } catch (Exception ex) {
      log.warn("Temporal reconciliation failed for run {}: {}", runId, ex.getMessage(), ex);
    }
  }

  private void resolveIfTerminal(
          @NonNull DslRun run,
          @NonNull WorkflowExecutionDescription description) {
    WorkflowExecutionStatus temporalStatus = description.getStatus();
    Instant closeTime = description.getCloseTime();

    if (temporalStatus == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED) {
      writeTerminal(run, DslRunStatus.COMPLETED, null, closeTime);
      return;
    }
    if (temporalStatus == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_FAILED) {
      writeTerminal(run, DslRunStatus.FAILED, "Workflow failed in Temporal", closeTime);
      return;
    }
    if (temporalStatus == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TIMED_OUT) {
      writeTerminal(run, DslRunStatus.FAILED, "Workflow timed out in Temporal", closeTime);
      return;
    }
    if (temporalStatus == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_CANCELED) {
      writeTerminal(run, DslRunStatus.CANCELLED, "Workflow was cancelled in Temporal", closeTime);
      return;
    }
    if (temporalStatus == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TERMINATED) {
      writeTerminal(run, DslRunStatus.CANCELLED, "Workflow was terminated in Temporal", closeTime);
      return;
    }

    // RUNNING, CONTINUED_AS_NEW, or UNSPECIFIED: leave the row alone and let the next pass
    // re-evaluate.
  }

  private void writeTerminal(
          @NonNull DslRun run,
          @NonNull DslRunStatus status,
          @Nullable String error,
          @Nullable Instant closeTime) {
    Instant finishedAt = closeTime != null ? closeTime : clock.instant();
    int affected = runRepository.updateFinishedIfRunning(
            run.runId(),
            status.name(),
            EMPTY_OUTPUT_JSON,
            error,
            finishedAt,
            null);
    if (affected > 0) {
      meterRegistry.counter(RESOLVED_COUNTER,
              PROCESS_NAME_TAG, safeProcessName(run.processName()),
              STATUS_TAG, status.name()).increment();
      log.info("Reconciled run {} to status {}", run.runId(), status.name());
    }
  }

  private static @NonNull String safeProcessName(@Nullable String processName) {
    return processName != null && !processName.isBlank() ? processName : UNKNOWN_PROCESS;
  }
}
