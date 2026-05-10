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
import cbs.dsl.api.EventTypes.EventStatus;
import cbs.dsl.api.EventOperation;
import cbs.dsl.api.ParameterDefinition;
import cbs.nova.model.EventExecutionRequest;
import cbs.nova.model.EventExecutionResponse;
import cbs.dsl.api.EventTypes.EventInput;
import cbs.nova.registry.DefaultSpecDefinitionRegistry;
import cbs.nova.registry.DslRegistry;
import cbs.nova.temporal.WorkflowManager;
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
        "LOAN_DISBURSEMENT", "admin1", Map.of("amount", 1000, "unknownParam", "value"));

    ParameterDefinition paramDef1 = mock(ParameterDefinition.class);
    when(paramDef1.getName()).thenReturn("amount");

    ParameterDefinition paramDef2 = mock(ParameterDefinition.class);
    when(paramDef2.getName()).thenReturn("accountId");

    when(dslRegistry.resolveEvent("LOAN_DISBURSEMENT")).thenReturn(mockEventDefinition);
    when(mockEventDefinition.getParameters()).thenReturn(List.of(paramDef1, paramDef2));
    doReturn(Object.class).when(specRegistry).getWorkflowInterface("LOAN_DISBURSEMENT");

    EventOperation mockEventOperation = mock(EventOperation.class);
    when(workflowManager.newWorkflowStub(eq("LOAN_DISBURSEMENT"), any()))
        .thenReturn(mockEventOperation);

    EventExecutionResponse response = eventRunner.perform(request);

    assertThat(response.status()).isEqualTo(EventStatus.PENDING);
    assertThat(response.executionId()).isEqualTo("123abc");

    verify(workflowManager).start(any(EventOperation.class), any());
  }

  @Test
  @DisplayName("shouldIncludeAllParamsWhenAllAreDefined")
  void shouldIncludeAllParamsWhenAllAreDefined() {
    EventExecutionRequest request = new EventExecutionRequest(
        "PAYMENT", "admin1", Map.of("amount", 500, "accountId", "ACC123"));

    ParameterDefinition paramDef1 = mock(ParameterDefinition.class);
    when(paramDef1.getName()).thenReturn("amount");

    ParameterDefinition paramDef2 = mock(ParameterDefinition.class);
    when(paramDef2.getName()).thenReturn("accountId");

    when(dslRegistry.resolveEvent("PAYMENT")).thenReturn(mockEventDefinition);
    when(mockEventDefinition.getParameters()).thenReturn(List.of(paramDef1, paramDef2));
    doReturn(Object.class).when(specRegistry).getWorkflowInterface("PAYMENT");

    EventOperation mockEventOperation = mock(EventOperation.class);
    when(workflowManager.newWorkflowStub(eq("PAYMENT"), any())).thenReturn(mockEventOperation);

    EventExecutionResponse response = eventRunner.perform(request);

    ArgumentCaptor<EventInput> inputCaptor = ArgumentCaptor.forClass(EventInput.class);
    verify(workflowManager).start(any(EventOperation.class), inputCaptor.capture());

    EventInput capturedInput = inputCaptor.getValue();
    assertThat(capturedInput.params()).hasSize(2);
    assertThat(capturedInput.params()).containsEntry("amount", 500);
    assertThat(capturedInput.params()).containsEntry("accountId", "ACC123");
  }

  @Test
  @DisplayName("shouldHandleEmptyParams")
  void shouldHandleEmptyParams() {
    EventExecutionRequest request = new EventExecutionRequest("SIMPLE_EVENT", "admin1", Map.of());

    when(dslRegistry.resolveEvent("SIMPLE_EVENT")).thenReturn(mockEventDefinition);
    when(mockEventDefinition.getParameters()).thenReturn(List.of());
    doReturn(Object.class).when(specRegistry).getWorkflowInterface("SIMPLE_EVENT");

    EventOperation mockEventOperation = mock(EventOperation.class);
    when(workflowManager.newWorkflowStub(eq("SIMPLE_EVENT"), any()))
        .thenReturn(mockEventOperation);

    EventExecutionResponse response = eventRunner.perform(request);

    assertThat(response.status()).isEqualTo(EventStatus.PENDING);

    ArgumentCaptor<EventInput> inputCaptor = ArgumentCaptor.forClass(EventInput.class);
    verify(workflowManager).start(any(EventOperation.class), inputCaptor.capture());
    assertThat(inputCaptor.getValue().params()).isEmpty();
  }

  @Test
  @DisplayName("shouldThrowWhenWorkflowNotRegistered")
  void shouldThrowWhenWorkflowNotRegistered() {
    EventExecutionRequest request =
        new EventExecutionRequest("UNKNOWN_EVENT", "admin1", Map.of("param", "value"));

    doThrow(new IllegalArgumentException("Workflow 'UNKNOWN_EVENT' not found"))
        .when(specRegistry)
        .getWorkflowInterface("UNKNOWN_EVENT");

    assertThatThrownBy(() -> eventRunner.perform(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("UNKNOWN_EVENT");
  }

  @Test
  @DisplayName("shouldThrowWhenEventNotInRegistry")
  void shouldThrowWhenEventNotInRegistry() {
    EventExecutionRequest request = new EventExecutionRequest("MISSING_EVENT", "admin1", Map.of());

    when(dslRegistry.resolveEvent("MISSING_EVENT"))
        .thenThrow(new IllegalArgumentException("Event 'MISSING_EVENT' not found"));

    assertThatThrownBy(() -> eventRunner.perform(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MISSING_EVENT");
  }
}
