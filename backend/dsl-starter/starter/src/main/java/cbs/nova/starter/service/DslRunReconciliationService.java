package cbs.nova.starter.service;

import static cbs.nova.starter.core.StarterConstants.SERVICE_SHUTDOWN_JOIN;
import static cbs.nova.starter.core.StarterConstants.UNKNOWN_PROCESS;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.metric.RunDurationMetricsCollector;
import cbs.nova.starter.sse.ExecutionStatusEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionDescription;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowStub;
import io.temporal.client.schedules.ScheduleActionExecutionStartWorkflow;
import io.temporal.client.schedules.ScheduleActionResult;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleListDescription;
import io.temporal.client.schedules.ScheduleListInfo;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Scheduled reconciliation that asks Temporal for the real outcome of stuck {@code RUNNING}
 * {@code dsl_runs} rows and writes the correct terminal status.
 *
 * <p>
 * This service complements, but does not replace, the existing STALE sweep in
 * {@link TemporalDslProcessService}: when Temporal cannot answer (for example because the workflow
 * was already reaped) the row is still eventually marked {@link DslRunStatus#STALE} by the
 * healthcheck sweep.
 *
 * <p>
 * T726: schedule-fired runs never pass through {@code TemporalDslProcessService.startProcess} (the
 * schedule action starts the workflow directly), so they have no {@code dsl_runs} row. When a
 * {@link ScheduleClient} is available, each pass also backfills rows for schedule executions still
 * visible in the schedules' recent-actions buffers: the row is inserted as {@code RUNNING} and is
 * then driven to its terminal state by the existing per-run sweep above — one write path, no
 * divergence. This is eventually consistent (lag = {@code scanInterval}); see docs/dsl/runtime.md.
 */
@Slf4j
public class DslRunReconciliationService {

  static final String PROCESS_NAME_TAG = "processName";
  static final String STATUS_TAG = "status";
  static final String EMPTY_OUTPUT_JSON = "{}";

  private final DslRunRepository runRepository;
  private final WorkflowClient workflowClient;
  private final MeterRegistry meterRegistry;
  private final Duration scanInterval;
  private final Duration gracePeriod;
  private final int batchSize;
  private final ScheduledExecutorService schedulingExecutor;
  private final Clock clock;
  private final @Nullable ScheduleClient scheduleClient;
  private @Nullable ExecutionStatusEventPublisher statusPublisher;

  public DslRunReconciliationService(
          DslRunRepository runRepository,
          WorkflowClient workflowClient,
          MeterRegistry meterRegistry,
          Duration scanInterval,
          Duration gracePeriod,
          int batchSize,
          ScheduledExecutorService schedulingExecutor,
          Clock clock) {
    this(runRepository, workflowClient, meterRegistry, scanInterval, gracePeriod, batchSize,
            schedulingExecutor, clock, null);
  }

  public DslRunReconciliationService(
          DslRunRepository runRepository,
          WorkflowClient workflowClient,
          MeterRegistry meterRegistry,
          Duration scanInterval,
          Duration gracePeriod,
          int batchSize,
          ScheduledExecutorService schedulingExecutor,
          Clock clock,
          @Nullable ScheduleClient scheduleClient) {
    this.runRepository = runRepository;
    this.workflowClient = workflowClient;
    this.meterRegistry = meterRegistry;
    this.scanInterval = scanInterval;
    this.gracePeriod = gracePeriod;
    this.batchSize = batchSize;
    this.schedulingExecutor = schedulingExecutor;
    this.clock = clock;
    this.scheduleClient = scheduleClient;
  }

  private final AtomicReference<ScheduledFuture<?>> handle = new AtomicReference<>();
  private final AtomicBoolean started = new AtomicBoolean(false);

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
      current.get(SERVICE_SHUTDOWN_JOIN.toMillis(), TimeUnit.MILLISECONDS);
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

  /**
   * One stuck-run sweep pass. Made {@code public} (was package-private) by T412 so the
   * {@code DslRunReconciliationMaintenanceTask} adapter in {@code cbs.nova.starter.maintenance} can
   * invoke it; existing tests in {@code cbs.nova.starter.service} retain the same access.
   */
  public void reconcileOnce() {
    backfillScheduleRunsSafely();
    Instant cutoff = clock.instant().minus(gracePeriod);
    runRepository.knownProcessNames().stream()
            .flatMap(processName -> runRepository.findByProcessName(processName).stream())
            .filter(run -> DslRunStatus.RUNNING.name().equals(run.status()))
            .filter(run -> run.startedAt().isBefore(cutoff))
            .limit(batchSize)
            .forEach(this::reconcileRun);
  }

  /**
   * T726: best-effort backfill of {@code dsl_runs} rows for schedule-fired workflows. Never throws
   * into the stuck-run sweep; a failure here only defers history capture to the next pass.
   */
  private void backfillScheduleRunsSafely() {
    try {
      backfillScheduleRuns();
    } catch (Exception ex) {
      log.warn("dsl_runs schedule-run backfill pass failed: {}", ex.getMessage(), ex);
    }
  }

  private void backfillScheduleRuns() {
    if (scheduleClient == null) {
      return;
    }
    List<ScheduleListDescription> schedules;
    try (Stream<ScheduleListDescription> stream = scheduleClient.listSchedules()) {
      schedules = stream
              .filter(schedule -> schedule.getScheduleId() != null
                      && schedule.getScheduleId().startsWith(StarterConstants.SCHEDULE_PREFIX))
              .toList();
    }
    for (ScheduleListDescription schedule : schedules) {
      backfillSchedule(schedule);
    }
  }

  private void backfillSchedule(@NonNull ScheduleListDescription schedule) {
    String processName = schedule.getScheduleId()
            .substring(StarterConstants.SCHEDULE_PREFIX.length());
    if (processName.isBlank()) {
      return;
    }
    ScheduleListInfo info = schedule.getInfo();
    if (info == null) {
      return;
    }
    for (ScheduleActionResult action : info.getRecentActions()) {
      try {
        backfillAction(processName, action);
      } catch (Exception ex) {
        log.warn("dsl_runs backfill failed for schedule {}: {}",
                schedule.getScheduleId(), ex.getMessage(), ex);
      }
    }
  }

  private void backfillAction(@NonNull String processName, @NonNull ScheduleActionResult action) {
    if (!(action.getAction() instanceof ScheduleActionExecutionStartWorkflow start)
            || start.getWorkflowId() == null
            || start.getWorkflowId().isBlank()) {
      return;
    }
    String workflowId = start.getWorkflowId();
    if (runRepository.findByRunId(workflowId).isPresent()) {
      return;
    }
    Instant startedAt = action.getStartedAt() != null ? action.getStartedAt() : clock.instant();
    DslRun scheduled = DslRun.builder()
            .runId(workflowId)
            .processName(processName)
            .status(DslRunStatus.RUNNING.name())
            // The schedule input payload is not reachable from schedule metadata; the row records
            // an empty object rather than guessing.
            .input(EMPTY_OUTPUT_JSON)
            .output(EMPTY_OUTPUT_JSON)
            .error(null)
            .startedAt(startedAt)
            .finishedAt(StarterConstants.NOT_FINISHED_AT)
            .executionMode(ExecutionMode.RUN.name())
            .triggeredBy(StarterConstants.SCHEDULE_TRIGGERED_BY)
            // A schedule fire carries no client correlation id.
            .correlationId(null)
            .definitionHash(RunDefinitionHash.of(processName))
            .contentHash(DefinitionContentHash.of(processName))
            .build();
    runRepository.save(scheduled);
    meterRegistry.counter(StarterConstants.BACKFILLED_COUNTER,
            PROCESS_NAME_TAG, safeProcessName(processName)).increment();
    publishStatusChanged(workflowId, DslRunStatus.RUNNING.name());
    log.info("Backfilled dsl_runs row {} for schedule-fired run of process {}",
            workflowId, processName);
  }

  private void reconcileRun(@NonNull DslRun run) {
    String runId = run.runId();
    meterRegistry.counter(StarterConstants.INSPECTED_COUNTER,
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

  @Autowired(required = false)
  public void setExecutionStatusEventPublisher(@Nullable ExecutionStatusEventPublisher publisher) {
    this.statusPublisher = publisher;
  }

  private void publishStatusChanged(@NonNull String runId, @NonNull String status) {
    if (statusPublisher == null) {
      return;
    }
    statusPublisher.publish(runId, status);
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
      meterRegistry.counter(StarterConstants.RESOLVED_COUNTER,
              PROCESS_NAME_TAG, safeProcessName(run.processName()),
              STATUS_TAG, status.name()).increment();
      new RunDurationMetricsCollector(meterRegistry).record(
              run.processName(), status.name(), Duration.between(run.startedAt(), finishedAt));
      publishStatusChanged(run.runId(), status.name());
      log.info("Reconciled run {} to status {}", run.runId(), status.name());
    }
  }

  private static @NonNull String safeProcessName(@Nullable String processName) {
    return processName != null && !processName.isBlank() ? processName : UNKNOWN_PROCESS;
  }
}
