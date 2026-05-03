package cbs.nova.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.nova.model.EventExecutionRequest;
import cbs.nova.model.EventExecutionResponse;
import cbs.nova.model.WorkflowExecutionResponse;
import cbs.nova.model.exception.EntityNotFoundException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

  @Mock
  private WorkflowResolver workflowResolver;

  @Mock
  private ContextEvaluator contextEvaluator;

  @Mock
  private ContextEncryptionService contextEncryptionService;

  @Mock
  private WorkflowExecutor workflowExecutor;

  @InjectMocks
  private EventService eventService;

  @Test
  @DisplayName("shouldReturnResponseWhenAllCollaboratorsSucceed")
  void shouldReturnResponseWhenAllCollaboratorsSucceed() {
    EventExecutionRequest request =
        new EventExecutionRequest("loan-workflow", "submit", "user1", Map.of("amount", 1000));
    WorkflowDefinition workflowDef = mock(WorkflowDefinition.class);
    EventDefinition eventDef = mock(EventDefinition.class);

    when(workflowResolver.resolve("loan-workflow")).thenReturn(workflowDef);
    when(workflowResolver.resolveEvent("submit")).thenReturn(eventDef);
    when(contextEvaluator.evaluate(eventDef, request))
        .thenReturn(Map.of("amount", 1000, "enriched", true));
    when(contextEncryptionService.encrypt(Map.of("amount", 1000, "enriched", true)))
        .thenReturn("{\"amount\":1000,\"enriched\":true}");
    when(workflowExecutor.start(request, "{\"amount\":1000,\"enriched\":true}"))
        .thenReturn(new WorkflowExecutionResponse(1L, "ACTIVE"));

    EventExecutionResponse response = eventService.execute(request);

    assertEquals(1L, response.executionId());
    assertEquals("ACTIVE", response.status());
    verify(workflowResolver).resolve("loan-workflow");
    verify(workflowResolver).resolveEvent("submit");
    verify(contextEvaluator).evaluate(eventDef, request);
    verify(contextEncryptionService).encrypt(Map.of("amount", 1000, "enriched", true));
    verify(workflowExecutor).start(request, "{\"amount\":1000,\"enriched\":true}");
  }

  @Test
  @DisplayName("shouldPropagateExceptionWhenWorkflowResolverThrows")
  void shouldPropagateExceptionWhenWorkflowResolverThrows() {
    EventExecutionRequest request =
        new EventExecutionRequest("missing-workflow", "submit", "user1", Map.of());
    when(workflowResolver.resolve("missing-workflow"))
        .thenThrow(new EntityNotFoundException("WorkflowDefinition", "missing-workflow"));

    assertThrows(EntityNotFoundException.class, () -> eventService.execute(request));
  }

  @Test
  @DisplayName("shouldPropagateExceptionWhenContextEvaluatorThrows")
  void shouldPropagateExceptionWhenContextEvaluatorThrows() {
    EventExecutionRequest request =
        new EventExecutionRequest("loan-workflow", "submit", "user1", Map.of());
    WorkflowDefinition workflowDef = mock(WorkflowDefinition.class);
    EventDefinition eventDef = mock(EventDefinition.class);

    when(workflowResolver.resolve("loan-workflow")).thenReturn(workflowDef);
    when(workflowResolver.resolveEvent("submit")).thenReturn(eventDef);
    when(contextEvaluator.evaluate(eventDef, request))
        .thenThrow(new RuntimeException("contextBlock failed"));

    assertThrows(RuntimeException.class, () -> eventService.execute(request));
  }
}
