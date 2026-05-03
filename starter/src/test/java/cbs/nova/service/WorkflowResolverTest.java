package cbs.nova.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.nova.model.exception.EntityNotFoundException;
import cbs.nova.registry.DslRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class WorkflowResolverTest {

  @Mock
  private DslRegistry dslRegistry;

  @InjectMocks
  private WorkflowResolver workflowResolver;

  @Test
  @DisplayName("shouldReturnWorkflowDefinitionWhenWorkflowExists")
  void shouldReturnWorkflowDefinitionWhenWorkflowExists() {
    WorkflowDefinition expected = mock(WorkflowDefinition.class);
    when(dslRegistry.getWorkflows()).thenReturn(Map.of("loan", expected));

    WorkflowDefinition result = workflowResolver.resolve("loan");

    assertEquals(expected, result);
  }

  @Test
  @DisplayName("shouldThrowEntityNotFoundExceptionWhenWorkflowNotFound")
  void shouldThrowEntityNotFoundExceptionWhenWorkflowNotFound() {
    when(dslRegistry.getWorkflows()).thenReturn(Map.of());

    EntityNotFoundException ex =
        assertThrows(EntityNotFoundException.class, () -> workflowResolver.resolve("nonexistent"));

    assertTrue(ex.getMessage().contains("WorkflowDefinition"));
    assertTrue(ex.getMessage().contains("nonexistent"));
  }

  @Test
  @DisplayName("shouldReturnEventDefinitionWhenEventExists")
  void shouldReturnEventDefinitionWhenEventExists() {
    EventDefinition expected = mock(EventDefinition.class);
    when(dslRegistry.getEvents()).thenReturn(Map.of("submit", expected));

    EventDefinition result = workflowResolver.resolveEvent("submit");

    assertEquals(expected, result);
  }

  @Test
  @DisplayName("shouldThrowEntityNotFoundExceptionWhenEventNotFound")
  void shouldThrowEntityNotFoundExceptionWhenEventNotFound() {
    when(dslRegistry.getEvents()).thenReturn(Map.of());

    EntityNotFoundException ex = assertThrows(
        EntityNotFoundException.class, () -> workflowResolver.resolveEvent("nonexistent"));

    assertTrue(ex.getMessage().contains("EventDefinition"));
    assertTrue(ex.getMessage().contains("nonexistent"));
  }
}
