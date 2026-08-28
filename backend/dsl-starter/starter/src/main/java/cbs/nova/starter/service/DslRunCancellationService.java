package cbs.nova.starter.service;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowStub;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

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

  public DslRunCancellationService(
          @NonNull WorkflowClient workflowClient,
          @NonNull DslRunRepository runRepository) {
    this(workflowClient, runRepository, Clock.systemUTC());
  }

  public DslRunCancellationService(
          @NonNull WorkflowClient workflowClient,
          @NonNull DslRunRepository runRepository,
          @NonNull Clock clock) {
    this.workflowClient = workflowClient;
    this.runRepository = runRepository;
    this.clock = clock;
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
      return new CancelResult(Outcome.NOT_FOUND, null, null);
    }

    DslRun run = existing.get();
    if (!DslRunStatus.RUNNING.name().equals(run.status())) {
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
      return new CancelResult(Outcome.NOT_CANCELLABLE, latest, latest.status());
    }

    log.info("Run {} cancelled by user request", runId);
    return new CancelResult(Outcome.CANCELLED, latest, latest.status());
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
