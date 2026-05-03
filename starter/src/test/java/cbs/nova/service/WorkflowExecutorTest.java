package cbs.nova.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.dsl.runtime.DslRegistry;
import cbs.nova.model.EventExecutionRequest;
import cbs.nova.model.EventWorkflowRequest;
import cbs.nova.model.WorkflowExecutionResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class WorkflowExecutorTest {

  @Mock
  private WorkflowClient workflowClient;

  @Mock
  private DslRegistry dslRegistry;

  @InjectMocks
  private WorkflowExecutor workflowExecutor;

  @Captor
  private ArgumentCaptor<EventWorkflowRequest> inputCaptor;

  @Captor
  private ArgumentCaptor<WorkflowOptions> optionsCaptor;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(workflowExecutor, "taskQueue", "WORKFLOW_TASK_QUEUE");
  }

  @Test
  @DisplayName("shouldReturnWorkflowExecutionResultWhenTemporalSucceeds")
  void shouldReturnWorkflowExecutionResultWhenTemporalSucceeds() {
    EventExecutionRequest request =
        new EventExecutionRequest("loan", "submit", "user1", Map.of("amount", 1000));
    String contextJson = "{\"amount\":1000}";
    EventWorkflow workflowStub = mock(EventWorkflow.class);
    WorkflowExecutionResponse expectedResult = new WorkflowExecutionResponse(42L, "ACTIVE");

    when(workflowClient.newWorkflowStub(eq(EventWorkflow.class), any(WorkflowOptions.class)))
        .thenReturn(workflowStub);
    when(workflowStub.execute(any(EventWorkflowRequest.class))).thenReturn(expectedResult);

    WorkflowExecutionResponse result = workflowExecutor.start(request, contextJson);

    assertEquals(42L, result.executionId());
    assertEquals("ACTIVE", result.status());

    verify(workflowClient).newWorkflowStub(eq(EventWorkflow.class), optionsCaptor.capture());
    WorkflowOptions capturedOptions = optionsCaptor.getValue();
    assertEquals("WORKFLOW_TASK_QUEUE", capturedOptions.getTaskQueue());

    verify(workflowStub).execute(inputCaptor.capture());
    EventWorkflowRequest capturedInput = inputCaptor.getValue();
    assertEquals("loan", capturedInput.workflowCode());
    assertEquals("submit", capturedInput.eventCode());
    assertEquals(contextJson, capturedInput.contextJson());
    assertEquals("user1", capturedInput.performedBy());
  }

  @Test
  @DisplayName("shouldPropagateWorkflowExceptionWhenTemporalFails")
  void shouldPropagateWorkflowExceptionWhenTemporalFails() {
    EventExecutionRequest request = new EventExecutionRequest("loan", "submit", "user1", Map.of());
    EventWorkflow workflowStub = mock(EventWorkflow.class);

    when(workflowClient.newWorkflowStub(eq(EventWorkflow.class), any(WorkflowOptions.class)))
        .thenReturn(workflowStub);
    when(workflowStub.execute(any(EventWorkflowRequest.class)))
        .thenThrow(new RuntimeException("temporal failure"));

    assertThrows(RuntimeException.class, () -> workflowExecutor.start(request, "{}"));
  }
}
