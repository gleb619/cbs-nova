package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowStub;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

class DslRunCancellationServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");
  private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

  private final WorkflowClient workflowClient = mock(WorkflowClient.class);
  private final InMemoryDslRunRepository repository = new InMemoryDslRunRepository();
  private final DslRunCancellationService service = new DslRunCancellationService(workflowClient,
          repository, FIXED);

  @Test
  void unknownRunIsReportedAsNotFoundWithoutTouchingTemporal() {
    DslRunCancellationService.CancelResult result = service.cancel("nope");

    assertThat(result.outcome()).isEqualTo(DslRunCancellationService.Outcome.NOT_FOUND);
    assertThat(result.run()).isNull();
    verifyNoInteractions(workflowClient);
  }

  @Test
  void nonRunningRunIsReportedAsNotCancellableWithoutTouchingTemporal() {
    repository.save(run("run-1", DslRunStatus.FAILED));

    DslRunCancellationService.CancelResult result = service.cancel("run-1");

    assertThat(result.outcome()).isEqualTo(DslRunCancellationService.Outcome.NOT_CANCELLABLE);
    assertThat(result.currentStatus()).isEqualTo("FAILED");
    verifyNoInteractions(workflowClient);
  }

  @Test
  void runningRunIsCancelledInTemporalAndRecordedAsCancelled() {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    repository.save(running("run-1"));

    DslRunCancellationService.CancelResult result = service.cancel("run-1");

    verify(stub).cancel();
    assertThat(result.outcome()).isEqualTo(DslRunCancellationService.Outcome.CANCELLED);
    DslRun stored = repository.findByRunId("run-1").orElseThrow();
    assertThat(stored.status()).isEqualTo(DslRunStatus.CANCELLED.name());
    assertThat(stored.error()).isEqualTo(DslRunCancellationService.CANCELLED_REASON);
    assertThat(stored.finishedAt()).isEqualTo(NOW);
  }

  @Test
  void missingTemporalWorkflowStillRecordsCancellation() {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    doThrow(new WorkflowNotFoundException(
            WorkflowExecution.newBuilder().setWorkflowId("run-1").build(),
            "LoanDisbursement",
            null))
            .when(stub).cancel();
    repository.save(running("run-1"));

    DslRunCancellationService.CancelResult result = service.cancel("run-1");

    assertThat(result.outcome()).isEqualTo(DslRunCancellationService.Outcome.CANCELLED);
    assertThat(repository.findByRunId("run-1").orElseThrow().status())
            .isEqualTo(DslRunStatus.CANCELLED.name());
  }

  @Test
  void guardedUpdateReturningZeroRowsIsReportedAsConflictNotSuccess() {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);

    DslRun racedToCompleted = run("run-1", DslRunStatus.COMPLETED);
    DslRunRepository racingRepository = mock(DslRunRepository.class);
    when(racingRepository.findByRunId("run-1"))
            .thenReturn(Optional.of(running("run-1")))
            .thenReturn(Optional.of(racedToCompleted));
    when(racingRepository.updateFinishedIfRunning(eq("run-1"), any(), any(), any(), any(), any()))
            .thenReturn(0);

    DslRunCancellationService racingService = new DslRunCancellationService(workflowClient,
            racingRepository, FIXED);

    DslRunCancellationService.CancelResult result = racingService.cancel("run-1");

    assertThat(result.outcome()).isEqualTo(DslRunCancellationService.Outcome.NOT_CANCELLABLE);
    assertThat(result.currentStatus()).isEqualTo(DslRunStatus.COMPLETED.name());
  }

  private static DslRun running(String runId) {
    return run(runId, DslRunStatus.RUNNING);
  }

  private static DslRun run(String runId, DslRunStatus status) {
    return DslRun.builder()
            .runId(runId)
            .processName("LoanDisbursement")
            .status(status.name())
            .startedAt(NOW.minusSeconds(30))
            .executionMode("RUN")
            .build();
  }
}
