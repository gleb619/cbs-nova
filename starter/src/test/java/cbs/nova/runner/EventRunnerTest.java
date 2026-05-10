package cbs.nova.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.ParameterDefinition;
import cbs.nova.model.EventExecutionRequest;
import cbs.nova.model.EventExecutionResponse;
import cbs.nova.registry.DefaultSpecDefinitionRegistry;
import cbs.nova.registry.DslRegistry;
import cbs.nova.temporal.WorkflowManager;
import io.temporal.client.WorkflowStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class EventRunnerTest {

  @Mock
  private WorkflowManager workflowManager;

  @Mock
  private DslRegistry dslRegistry;

  @Mock
  private DefaultSpecDefinitionRegistry specRegistry;

  @InjectMocks
  private EventRunner eventRunner;

  private EventDefinition mockEventDefinition;

  @BeforeEach
  void setUp() {
    mockEventDefinition = mock(EventDefinition.class);
  }

  @Test
  @DisplayName("shouldFilterParamsAndStartWorkflow")
  void shouldFilterParamsAndStartWorkflow() {
    EventExecutionRequest request = new EventExecutionRequest(
        "LOAN_DISBURSEMENT",
        "admin1",
        Map.of("amount", 1000, "unknownParam", "value"));

    ParameterDefinition paramDef1 = mock(ParameterDefinition.class);
    when(paramDef1.getName()).thenReturn("amount");

    ParameterDefinition paramDef2 = mock(ParameterDefinition.class);
    when(paramDef2.getName()).thenReturn("accountId");

    when(dslRegistry.resolveEvent("LOAN_DISBURSEMENT")).thenReturn(mockEventDefinition);
    when(mockEventDefinition.getParameters()).thenReturn(List.of(paramDef1, paramDef2));
    doReturn(Object.class).when(specRegistry).getWorkflowInterface("LOAN_DISBURSEMENT");

    WorkflowStub mockWorkflowStub = mock(WorkflowStub.class);
    when(workflowManager.newUntypedWorkflowStub(eq("LOAN_DISBURSEMENT"), any())).thenReturn(mockWorkflowStub);

    EventExecutionResponse response = eventRunner.perform(request);

    assertThat(response.status()).isEqualTo("STARTED");
    assertThat(response.executionId()).isNull();

    ArgumentCaptor<EventExecutionRequest> requestCaptor = ArgumentCaptor.forClass(EventExecutionRequest.class);
    verify(mockWorkflowStub).start(requestCaptor.capture());

    EventExecutionRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.params()).containsKey("amount");
    assertThat(capturedRequest.params()).doesNotContainKey("unknownParam");
  }

  @Test
  @DisplayName("shouldIncludeAllParamsWhenAllAreDefined")
  void shouldIncludeAllParamsWhenAllAreDefined() {
    EventExecutionRequest request = new EventExecutionRequest(
        "PAYMENT",
        "admin1",
        Map.of("amount", 500, "accountId", "ACC123"));

    ParameterDefinition paramDef1 = mock(ParameterDefinition.class);
    when(paramDef1.getName()).thenReturn("amount");

    ParameterDefinition paramDef2 = mock(ParameterDefinition.class);
    when(paramDef2.getName()).thenReturn("accountId");

    when(dslRegistry.resolveEvent("PAYMENT")).thenReturn(mockEventDefinition);
    when(mockEventDefinition.getParameters()).thenReturn(List.of(paramDef1, paramDef2));
    doReturn(Object.class).when(specRegistry).getWorkflowInterface("PAYMENT");

    WorkflowStub mockWorkflowStub = mock(WorkflowStub.class);
    when(workflowManager.newUntypedWorkflowStub(eq("PAYMENT"), any())).thenReturn(mockWorkflowStub);

    EventExecutionResponse response = eventRunner.perform(request);

    ArgumentCaptor<EventExecutionRequest> requestCaptor = ArgumentCaptor.forClass(EventExecutionRequest.class);
    verify(mockWorkflowStub).start(requestCaptor.capture());

    EventExecutionRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.params()).hasSize(2);
    assertThat(capturedRequest.params()).containsEntry("amount", 500);
    assertThat(capturedRequest.params()).containsEntry("accountId", "ACC123");
  }

  @Test
  @DisplayName("shouldHandleEmptyParams")
  void shouldHandleEmptyParams() {
    EventExecutionRequest request = new EventExecutionRequest(
        "SIMPLE_EVENT",
        "admin1",
        Map.of());

    when(dslRegistry.resolveEvent("SIMPLE_EVENT")).thenReturn(mockEventDefinition);
    when(mockEventDefinition.getParameters()).thenReturn(List.of());
    doReturn(Object.class).when(specRegistry).getWorkflowInterface("SIMPLE_EVENT");

    WorkflowStub mockWorkflowStub = mock(WorkflowStub.class);
    when(workflowManager.newUntypedWorkflowStub(eq("SIMPLE_EVENT"), any())).thenReturn(mockWorkflowStub);

    EventExecutionResponse response = eventRunner.perform(request);

    assertThat(response.status()).isEqualTo("STARTED");

    ArgumentCaptor<EventExecutionRequest> requestCaptor = ArgumentCaptor.forClass(EventExecutionRequest.class);
    verify(mockWorkflowStub).start(requestCaptor.capture());
    assertThat(requestCaptor.getValue().params()).isEmpty();
  }

  @Test
  @DisplayName("shouldThrowWhenWorkflowNotRegistered")
  void shouldThrowWhenWorkflowNotRegistered() {
    EventExecutionRequest request = new EventExecutionRequest(
        "UNKNOWN_EVENT",
        "admin1",
        Map.of("param", "value"));

    doThrow(new IllegalArgumentException("Workflow 'UNKNOWN_EVENT' not found"))
        .when(specRegistry).getWorkflowInterface("UNKNOWN_EVENT");

    assertThatThrownBy(() -> eventRunner.perform(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("UNKNOWN_EVENT");
  }

  @Test
  @DisplayName("shouldThrowWhenEventNotInRegistry")
  void shouldThrowWhenEventNotInRegistry() {
    EventExecutionRequest request = new EventExecutionRequest(
        "MISSING_EVENT",
        "admin1",
        Map.of());

    when(dslRegistry.resolveEvent("MISSING_EVENT"))
        .thenThrow(new IllegalArgumentException("Event 'MISSING_EVENT' not found"));

    assertThatThrownBy(() -> eventRunner.perform(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MISSING_EVENT");
  }

  @Test
  @DisplayName("shouldPreservePerformedByInFilteredRequest")
  void shouldPreservePerformedByInFilteredRequest() {
    EventExecutionRequest request = new EventExecutionRequest(
        "TEST_EVENT",
        "specific-performer",
        Map.of("extra", "value"));

    ParameterDefinition paramDef = mock(ParameterDefinition.class);
    when(paramDef.getName()).thenReturn("required");

    when(dslRegistry.resolveEvent("TEST_EVENT")).thenReturn(mockEventDefinition);
    when(mockEventDefinition.getParameters()).thenReturn(List.of(paramDef));
    doReturn(Object.class).when(specRegistry).getWorkflowInterface("TEST_EVENT");

    WorkflowStub mockWorkflowStub = mock(WorkflowStub.class);
    when(workflowManager.newUntypedWorkflowStub(eq("TEST_EVENT"), any())).thenReturn(mockWorkflowStub);

    eventRunner.perform(request);

    ArgumentCaptor<EventExecutionRequest> requestCaptor = ArgumentCaptor.forClass(EventExecutionRequest.class);
    verify(mockWorkflowStub).start(requestCaptor.capture());

    assertThat(requestCaptor.getValue().performedBy()).isEqualTo("specific-performer");
  }

  @Test
  @DisplayName("shouldPreserveEventCodeInFilteredRequest")
  void shouldPreserveEventCodeInFilteredRequest() {
    EventExecutionRequest request = new EventExecutionRequest(
        "MY_EVENT_CODE",
        "admin",
        Map.of("extra", "value"));

    ParameterDefinition paramDef = mock(ParameterDefinition.class);
    when(paramDef.getName()).thenReturn("required");

    when(dslRegistry.resolveEvent("MY_EVENT_CODE")).thenReturn(mockEventDefinition);
    when(mockEventDefinition.getParameters()).thenReturn(List.of(paramDef));
    doReturn(Object.class).when(specRegistry).getWorkflowInterface("MY_EVENT_CODE");

    WorkflowStub mockWorkflowStub = mock(WorkflowStub.class);
    when(workflowManager.newUntypedWorkflowStub(eq("MY_EVENT_CODE"), any())).thenReturn(mockWorkflowStub);

    eventRunner.perform(request);

    ArgumentCaptor<EventExecutionRequest> requestCaptor = ArgumentCaptor.forClass(EventExecutionRequest.class);
    verify(mockWorkflowStub).start(requestCaptor.capture());

    assertThat(requestCaptor.getValue().eventCode()).isEqualTo("MY_EVENT_CODE");
  }
}