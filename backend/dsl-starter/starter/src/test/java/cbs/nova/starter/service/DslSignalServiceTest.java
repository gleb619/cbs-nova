package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowStub;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DslSignalServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

  private final WorkflowClient workflowClient = mock(WorkflowClient.class);
  private final InMemoryDslRunRepository repository = new InMemoryDslRunRepository(
          InMemoryDslRunRepository.NO_OP_EVICTION);
  private final DslSignalService service = new DslSignalService(workflowClient, repository);

  @Test
  void unknownRunIsReportedAsNotFoundWithoutTouchingTemporal() {
    DslSignalService.SignalResult result = service.sendSignal("nope", "approve", null);

    assertThat(result.outcome()).isEqualTo(DslSignalService.Outcome.NOT_FOUND);
    assertThat(result.run()).isNull();
    assertThat(result.currentStatus()).isNull();
    verifyNoInteractions(workflowClient);
  }

  @Test
  void nonRunningRunIsReportedAsNotRunningWithoutTouchingTemporal() {
    repository.save(run("run-1", DslRunStatus.COMPLETED));

    DslSignalService.SignalResult result = service.sendSignal("run-1", "approve", null);

    assertThat(result.outcome()).isEqualTo(DslSignalService.Outcome.NOT_RUNNING);
    assertThat(result.run()).isNotNull();
    assertThat(result.currentStatus()).isEqualTo("COMPLETED");
    verifyNoInteractions(workflowClient);
  }

  @Test
  void runningRunSendsSignalWithPayloadAndReportsSent() {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    repository.save(running("run-1"));

    DslSignalService.SignalResult result = service.sendSignal("run-1", "approve",
            Map.of("approved", true));

    verify(stub).signal("approve", Map.of("approved", true));
    assertThat(result.outcome()).isEqualTo(DslSignalService.Outcome.SENT);
    assertThat(result.run()).isNotNull();
    assertThat(result.currentStatus()).isEqualTo("RUNNING");
  }

  @Test
  void runningRunSendsSignalWithoutPayloadWhenPayloadIsNull() {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    repository.save(running("run-1"));

    DslSignalService.SignalResult result = service.sendSignal("run-1", "approve", null);

    verify(stub).signal("approve");
    assertThat(result.outcome()).isEqualTo(DslSignalService.Outcome.SENT);
  }

  @Test
  void signalFailureFromStubIsPropagated() {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    doThrow(workflowNotFound()).when(stub).signal("approve");
    repository.save(running("run-1"));

    assertThatThrownBy(() -> service.sendSignal("run-1", "approve", null))
            .isInstanceOf(WorkflowNotFoundException.class);
  }

  @Test
  void querySignalStateReturnsMappedStateForRunningRun() {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    Map<String, Object> state = Map.of("lastSignal", "approve", "count", 3);
    doReturn(state).when(stub).query(eq("dslSignalState"), eq(Map.class));
    repository.save(running("run-1"));

    Map<String, Object> result = service.querySignalState("run-1");

    assertThat(result).isEqualTo(state);
  }

  @Test
  void querySignalStateReturnsNullForUnknownRun() {
    assertThat(service.querySignalState("nope")).isNull();
    verifyNoInteractions(workflowClient);
  }

  @Test
  void querySignalStateReturnsEmptyMapForNonRunningRun() {
    repository.save(run("run-1", DslRunStatus.FAILED));

    assertThat(service.querySignalState("run-1")).isEmpty();
    verifyNoInteractions(workflowClient);
  }

  @Test
  void querySignalStateReturnsEmptyMapWhenStubThrows() {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    doThrow(workflowNotFound()).when(stub).query(eq("dslSignalState"), eq(Map.class));
    repository.save(running("run-1"));

    assertThat(service.querySignalState("run-1")).isEmpty();
  }

  private static WorkflowNotFoundException workflowNotFound() {
    return new WorkflowNotFoundException(
            io.temporal.api.common.v1.WorkflowExecution.newBuilder()
                    .setWorkflowId("run-1")
                    .build(),
            "LoanDisbursement",
            null);
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
