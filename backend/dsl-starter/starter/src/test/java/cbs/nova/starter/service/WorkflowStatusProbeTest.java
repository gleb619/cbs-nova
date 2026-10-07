package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.history.DslRunStatus;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionDescription;
import io.temporal.client.WorkflowStub;
import org.junit.jupiter.api.Test;

import java.time.Instant;

class WorkflowStatusProbeTest {

  @Test
  void probeMapsTimedOutToFailedWithCloseTime() {
    WorkflowClient client = mock(WorkflowClient.class);
    WorkflowStub stub = mock(WorkflowStub.class);
    WorkflowExecutionDescription description = mock(WorkflowExecutionDescription.class);

    Instant closeTime = Instant.parse("2026-10-07T10:00:00Z");
    when(description.getStatus()).thenReturn(
            WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TIMED_OUT);
    when(description.getCloseTime()).thenReturn(closeTime);
    when(client.newUntypedWorkflowStub("run-1")).thenReturn(stub);
    when(stub.describe()).thenReturn(description);

    WorkflowStatusProbe probe = new WorkflowStatusProbe(client);
    WorkflowStatusProbe.ProbeResult result = probe.probe("run-1");

    assertThat(result).isInstanceOf(WorkflowStatusProbe.TerminalResult.class);
    WorkflowStatusProbe.TerminalResult terminal = (WorkflowStatusProbe.TerminalResult) result;
    assertThat(terminal.status()).isEqualTo(DslRunStatus.FAILED);
    assertThat(terminal.error()).contains("timed out");
    assertThat(terminal.closeTime()).isEqualTo(closeTime);
  }
}
