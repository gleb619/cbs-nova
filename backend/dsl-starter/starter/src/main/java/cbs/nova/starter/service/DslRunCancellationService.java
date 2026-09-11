package cbs.nova.starter.service;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.sse.ExecutionStatusEventPublisher;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowStub;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

/**
 * User-initiated cancellation of a RUNNING DSL process run.
 *
 * <p>
 * Sibling of {@link TemporalDslProcessService}: that service only ever ends a run passively, either
 * by the completion path or by the healthcheck staleness sweep (which marks runs STALE after
 * {@code cbs.nova.process.healthcheck.stale-threshold}). This service gives operators an immediate,
 * explicit stop: it asks Temporal to cancel the workflow whose workflow id is the run id, then
 * records a terminal {@link DslRunStatus#CANCELLED} row.
 *
 * <p>
 * The status write uses {@link DslRunRepository#updateFinishedIfRunning} — the same guarded
 * compare-and-set the staleness sweep uses — so a run that completes or fails concurrently with a
 * cancel request is never overwritten to CANCELLED. A zero-affected-rows result is surfaced as
 * {@link Outcome#NOT_CANCELLABLE} rather than treated as success, which is what lets the HTTP layer
 * answer 409 for that race.
 */
@Slf4j
public class DslRunCancellationService {

  /** Recorded in {@code dsl_run.error} so the reason a run ended is self-describing. */
  public static final String CANCELLED_REASON = "Cancelled by user";

  private static final String EMPTY_OUTPUT_JSON = "{}";

  private final WorkflowClient workflowClient;
  private final DslRunRepository runRepository;
  private final Clock clock;
  private final TemporalDslProcessService metricsRecorder;
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;
  private final ObjectProvider<TransactionTemplate> transactionTemplateProvider;
  private @Nullable ExecutionStatusEventPublisher statusPublisher;

  public DslRunCancellationService(
          @NonNull WorkflowClient workflowClient,
          @NonNull DslRunRepository runRepository) {
    this(workflowClient, runRepository, Clock.systemUTC(), null,
            EmptyObjectProvider.of(DomainEventPublisher.class),
            EmptyObjectProvider.of(TransactionTemplate.class));
  }

  public DslRunCancellationService(
          @NonNull WorkflowClient workflowClient,
          @NonNull DslRunRepository runRepository,
          @NonNull Clock clock) {
    this(workflowClient, runRepository, clock, null,
            EmptyObjectProvider.of(DomainEventPublisher.class),
            EmptyObjectProvider.of(TransactionTemplate.class));
  }

  public DslRunCancellationService(
          @NonNull WorkflowClient workflowClient,
          @NonNull DslRunRepository runRepository,
          @NonNull Clock clock,
          @Nullable TemporalDslProcessService metricsRecorder) {
    this(workflowClient, runRepository, clock, metricsRecorder,
            EmptyObjectProvider.of(DomainEventPublisher.class),
            EmptyObjectProvider.of(TransactionTemplate.class));
  }

  public DslRunCancellationService(
          @NonNull WorkflowClient workflowClient,
          @NonNull DslRunRepository runRepository,
          @NonNull Clock clock,
          @Nullable TemporalDslProcessService metricsRecorder,
          @NonNull ObjectProvider<DomainEventPublisher> eventPublisherProvider,
          @NonNull ObjectProvider<TransactionTemplate> transactionTemplateProvider) {
    this.workflowClient = workflowClient;
    this.runRepository = runRepository;
    this.clock = clock;
    this.metricsRecorder = metricsRecorder;
    this.eventPublisherProvider = eventPublisherProvider;
    this.transactionTemplateProvider = transactionTemplateProvider;
  }

  public enum Outcome {
    CANCELLED, NOT_FOUND, NOT_CANCELLABLE
  }

  public record CancelResult(
          @NonNull Outcome outcome,
          @Nullable DslRun run,
          @Nullable String currentStatus) {
  }

  public @NonNull CancelResult cancel(@NonNull String runId) {
    Optional<DslRun> existing = runRepository.findByRunId(runId);
    if (existing.isEmpty()) {
      Instant finishedAt = clock.instant();
      recordCancel(null, null, Outcome.NOT_FOUND, finishedAt);
      return new CancelResult(Outcome.NOT_FOUND, null, null);
    }

    DslRun run = existing.get();
    if (!DslRunStatus.RUNNING.name().equals(run.status())) {
      Instant finishedAt = clock.instant();
      recordCancel(run.processName(), run.startedAt(), Outcome.NOT_CANCELLABLE, finishedAt);
      return new CancelResult(Outcome.NOT_CANCELLABLE, run, run.status());
    }

    requestWorkflowCancellation(runId);

    Instant finishedAt = clock.instant();
    int affected = runRepository.updateFinishedIfRunning(
            runId,
            DslRunStatus.CANCELLED.name(),
            EMPTY_OUTPUT_JSON,
            CANCELLED_REASON,
            finishedAt,
            null);

    DslRun latest = runRepository.findByRunId(runId).orElse(run);
    if (affected == 0) {
      log.info("Cancel skipped terminal write for run {}: it is no longer RUNNING "
              + "(concurrent terminal transition, now {})", runId, latest.status());
      recordCancel(run.processName(), run.startedAt(), Outcome.NOT_CANCELLABLE, finishedAt);
      return new CancelResult(Outcome.NOT_CANCELLABLE, latest, latest.status());
    }

    // T411: emit RunCancelled in the same DB transaction as the terminal status row. With
    // asyncDbSave=false the publisher runs synchronously inside TransactionTemplate; with
    // asyncDbSave=true both writes happen later on the executor — each in its own TX, both
    // succeed-or-rollback together per task. With the in-memory repository there is no TX
    // (best-effort; see the loop note in the plan file).
    DomainEvent.RunCancelled cancelledEvent = new DomainEvent.RunCancelled(
            runId, run.processName(), DslRunStatus.CANCELLED,
            CANCELLED_REASON, run.startedAt(), finishedAt,
            finishedAt, run.correlationId());
    publishEvent(cancelledEvent);
    publishStatusChanged(runId, DslRunStatus.CANCELLED.name());

    log.info("Run {} cancelled by user request", runId);
    recordCancel(run.processName(), run.startedAt(), Outcome.CANCELLED, finishedAt);
    return new CancelResult(Outcome.CANCELLED, latest, latest.status());
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

  /**
   * Publishes a domain event through the optional {@link DomainEventPublisher} bean.
   *
   * <p>
   * When both the publisher and a {@link TransactionTemplate} are available, the event insert runs
   * in the SAME transaction as the surrounding state-row write (same TX boundary as the run
   * lifecycle sites in {@link TemporalDslProcessService}). The publisher is intentionally optional:
   * tests / in-memory runs construct this service without an event store, and the call becomes a
   * no-op. Failure is NOT swallowed at the run path — losing an event row would defeat the store's
   * purpose.
   */
  private void publishEvent(@NonNull DomainEvent event) {
    DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      return;
    }
    TransactionTemplate tx = transactionTemplateProvider.getIfAvailable();
    if (tx != null) {
      tx.executeWithoutResult(status -> publisher.publish(event));
    } else {
      publisher.publish(event);
    }
  }

  private void recordCancel(@Nullable String processName, @Nullable Instant startedAt,
          @NonNull Outcome outcome, @NonNull Instant finishedAt) {
    if (metricsRecorder == null) {
      return;
    }
    metricsRecorder.recordCancel(processName, startedAt, outcome, finishedAt);
  }

  /**
   * Ask Temporal to cancel the workflow whose workflow id is {@code runId}.
   *
   * <p>
   * Temporal Java SDK 1.27 has no {@code WorkflowClient#getWorkflowHandle}; the equivalent "handle
   * by workflow id" API is {@link WorkflowClient#newUntypedWorkflowStub(String)}, so that is what
   * we call {@link WorkflowStub#cancel()} on. A missing workflow is not an error: runs executed
   * without a Temporal workflow and runs whose history was already reaped both land here, and the
   * guarded terminal write that follows still satisfies the operator's intent.
   */
  private void requestWorkflowCancellation(@NonNull String runId) {
    try {
      workflowClient.newUntypedWorkflowStub(runId).cancel();
    } catch (WorkflowNotFoundException notFound) {
      log.warn("No Temporal workflow found for run {}; recording cancellation anyway", runId);
    }
  }
}
