package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import com.google.protobuf.Timestamp;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionDescription;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowStub;
import io.temporal.common.converter.DataConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.mockito.ArgumentMatchers;

class DslRunReconciliationServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");
  private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final Duration SCAN_INTERVAL = Duration.ofMinutes(5);
  private static final Duration GRACE_PERIOD = Duration.ofMinutes(15);
  private static final int BATCH_SIZE = 200;

  private final WorkflowClient workflowClient = mock(WorkflowClient.class);
  private final WorkflowStub stub = mock(WorkflowStub.class);
  private final InMemoryDslRunRepository repository = new InMemoryDslRunRepository();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);

  private final DslRunReconciliationService service = new DslRunReconciliationService(
          repository,
          workflowClient,
          meterRegistry,
          SCAN_INTERVAL,
          GRACE_PERIOD,
          BATCH_SIZE,
          executor,
          FIXED);

  @BeforeEach
  void setUp() {
    when(workflowClient.newUntypedWorkflowStub(anyString())).thenReturn(stub);
  }

  @Test
  void completedWorkflowIsRecordedAsCompleted() {
    assertReconciledTo(
            WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED,
            DslRunStatus.COMPLETED,
            null);
  }

  @Test
  void failedWorkflowIsRecordedAsFailed() {
    assertReconciledTo(
            WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_FAILED,
            DslRunStatus.FAILED,
            "failed");
  }

  @Test
  void timedOutWorkflowIsRecordedAsFailed() {
    assertReconciledTo(
            WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TIMED_OUT,
            DslRunStatus.FAILED,
            "timed out");
  }

  @Test
  void canceledWorkflowIsRecordedAsCancelled() {
    assertReconciledTo(
            WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_CANCELED,
            DslRunStatus.CANCELLED,
            "cancelled");
  }

  @Test
  void terminatedWorkflowIsRecordedAsCancelled() {
    assertReconciledTo(
            WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TERMINATED,
            DslRunStatus.CANCELLED,
            "terminated");
  }

  @Test
  void continuedAsNewAndRunningAreLeftAlone() {
    repository.save(runningOld("run-continued"));
    repository.save(runningOld("run-still-running"));
    when(stub.describe()).thenReturn(
            description(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW, null),
            description(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING, null));

    service.reconcileOnce();

    assertThat(repository.findByRunId("run-continued").orElseThrow().status())
            .isEqualTo(DslRunStatus.RUNNING.name());
    assertThat(repository.findByRunId("run-still-running").orElseThrow().status())
            .isEqualTo(DslRunStatus.RUNNING.name());
    assertThat(meterRegistry.find(DslRunReconciliationService.RESOLVED_COUNTER)
            .tag(DslRunReconciliationService.STATUS_TAG, DslRunStatus.RUNNING.name())
            .counter()).isNull();
    assertThat(meterRegistry.counter(DslRunReconciliationService.INSPECTED_COUNTER,
            DslRunReconciliationService.PROCESS_NAME_TAG, "LoanDisbursement").count())
            .isEqualTo(2);
  }

  @Test
  void missingTemporalWorkflowIsMarkedStale() {
    repository.save(runningOld("run-gone"));
    doThrow(new WorkflowNotFoundException(
            WorkflowExecution.newBuilder().setWorkflowId("run-gone").build(),
            "LoanDisbursement",
            null))
            .when(stub).describe();

    service.reconcileOnce();

    DslRun run = repository.findByRunId("run-gone").orElseThrow();
    assertThat(run.status()).isEqualTo(DslRunStatus.STALE.name());
    assertThat(run.finishedAt()).isEqualTo(NOW);
    assertThat(run.error()).contains("not found");
    assertThat(meterRegistry.counter(DslRunReconciliationService.RESOLVED_COUNTER,
            DslRunReconciliationService.PROCESS_NAME_TAG, "LoanDisbursement",
            DslRunReconciliationService.STATUS_TAG, DslRunStatus.STALE.name()).count())
            .isEqualTo(1);
  }

  @Test
  void transientTemporalErrorLeavesRowRunning() {
    repository.save(runningOld("run-transient"));
    doThrow(new RuntimeException("Temporal unavailable")).when(stub).describe();

    service.reconcileOnce();

    assertThat(repository.findByRunId("run-transient").orElseThrow().status())
            .isEqualTo(DslRunStatus.RUNNING.name());
  }

  @Test
  void runningRunInsideGracePeriodIsNotInspected() {
    repository.save(running("run-fresh", NOW.minusSeconds(600)));
    repository.save(runningOld("run-old"));
    when(stub.describe()).thenReturn(
            description(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED, NOW));

    service.reconcileOnce();

    assertThat(repository.findByRunId("run-fresh").orElseThrow().status())
            .isEqualTo(DslRunStatus.RUNNING.name());
    assertThat(repository.findByRunId("run-old").orElseThrow().status())
            .isEqualTo(DslRunStatus.COMPLETED.name());
    verify(stub, times(1)).describe();
  }

  @Test
  void secondPassIsIdempotent() {
    repository.save(runningOld("run-once"));
    when(stub.describe()).thenReturn(
            description(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED, NOW));

    service.reconcileOnce();
    service.reconcileOnce();

    assertThat(repository.findByRunId("run-once").orElseThrow().status())
            .isEqualTo(DslRunStatus.COMPLETED.name());
    verify(stub, times(1)).describe();
  }

  @Test
  void batchSizeCapsNumberOfInspects() {
    int batchSize = 2;
    DslRunReconciliationService smallBatchService = new DslRunReconciliationService(
            repository,
            workflowClient,
            meterRegistry,
            SCAN_INTERVAL,
            GRACE_PERIOD,
            batchSize,
            executor,
            FIXED);
    repository.save(runningOld("run-batch-1"));
    repository.save(runningOld("run-batch-2"));
    repository.save(runningOld("run-batch-3"));
    doThrow(new WorkflowNotFoundException(
            WorkflowExecution.newBuilder().setWorkflowId("ignored").build(),
            "LoanDisbursement",
            null))
            .when(stub).describe();

    smallBatchService.reconcileOnce();

    verify(stub, times(batchSize)).describe();
    List<DslRun> runs = repository.findByProcessName("LoanDisbursement");
    long staleCount = runs.stream()
            .filter(r -> DslRunStatus.STALE.name().equals(r.status()))
            .count();
    long runningCount = runs.stream()
            .filter(r -> DslRunStatus.RUNNING.name().equals(r.status()))
            .count();
    assertThat(staleCount).isEqualTo(batchSize);
    assertThat(runningCount).isEqualTo(1);
  }

  @Test
  void startDoesNothingWhenAlreadyStarted() {
    service.start();
    service.start();
    verify(executor, times(1)).scheduleWithFixedDelay(
            ArgumentMatchers.any(Runnable.class),
            ArgumentMatchers.anyLong(),
            ArgumentMatchers.anyLong(),
            ArgumentMatchers.any());
  }

  private void assertReconciledTo(
          WorkflowExecutionStatus temporalStatus,
          DslRunStatus dslStatus,
          String errorPart) {
    reset(stub);
    when(workflowClient.newUntypedWorkflowStub(anyString())).thenReturn(stub);
    String runId = "run-" + temporalStatus.name().toLowerCase();
    repository.save(runningOld(runId));
    when(stub.describe()).thenReturn(description(temporalStatus, NOW));

    service.reconcileOnce();

    DslRun run = repository.findByRunId(runId).orElseThrow();
    assertThat(run.status()).isEqualTo(dslStatus.name());
    assertThat(run.finishedAt()).isEqualTo(NOW);
    assertThat(run.output()).isEqualTo(DslRunReconciliationService.EMPTY_OUTPUT_JSON);
    if (errorPart == null) {
      assertThat(run.error()).isNull();
    } else {
      assertThat(run.error()).containsIgnoringCase(errorPart);
    }
    assertThat(meterRegistry.counter(DslRunReconciliationService.RESOLVED_COUNTER,
            DslRunReconciliationService.PROCESS_NAME_TAG, "LoanDisbursement",
            DslRunReconciliationService.STATUS_TAG, dslStatus.name()).count())
            .isEqualTo(1);
  }

  private static WorkflowExecutionDescription description(
          WorkflowExecutionStatus status,
          Instant closeTime) {
    WorkflowExecutionInfo.Builder info = WorkflowExecutionInfo.newBuilder().setStatus(status);
    if (closeTime != null) {
      info.setCloseTime(Timestamp.newBuilder()
              .setSeconds(closeTime.getEpochSecond())
              .setNanos(closeTime.getNano())
              .build());
    }
    DescribeWorkflowExecutionResponse response = DescribeWorkflowExecutionResponse.newBuilder()
            .setWorkflowExecutionInfo(info.build())
            .build();
    return new WorkflowExecutionDescription(response, DataConverter.getDefaultInstance());
  }

  private static DslRun runningOld(String runId) {
    return running(runId, NOW.minusSeconds(1200));
  }

  private static DslRun running(String runId, Instant startedAt) {
    return DslRun.builder()
            .runId(runId)
            .processName("LoanDisbursement")
            .status(DslRunStatus.RUNNING.name())
            .startedAt(startedAt)
            .executionMode("RUN")
            .build();
  }
}
