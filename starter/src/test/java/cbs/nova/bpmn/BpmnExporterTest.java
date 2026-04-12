package cbs.nova.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.runtime.DslRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

class BpmnExporterTest {

  @Test
  @DisplayName("shouldReturnXmlWhenWorkflowExists")
  void shouldReturnXmlWhenWorkflowExists() {
    // Given
    DslRegistry registry = mock(DslRegistry.class);
    StaticBpmnGenerator generator = mock(StaticBpmnGenerator.class);
    WorkflowDefinition workflow = mock(WorkflowDefinition.class);

    when(registry.getWorkflows()).thenReturn(Map.of("loan-approval", workflow));
    when(generator.generate(workflow)).thenReturn("<xml/>");

    BpmnExporter exporter = new BpmnExporter(registry, generator);

    // When
    String result = exporter.export("loan-approval");

    // Then
    assertThat(result).isEqualTo("<xml/>");
  }

  @Test
  @DisplayName("shouldThrowWhenWorkflowNotFound")
  void shouldThrowWhenWorkflowNotFound() {
    // Given
    DslRegistry registry = mock(DslRegistry.class);
    StaticBpmnGenerator generator = mock(StaticBpmnGenerator.class);

    when(registry.getWorkflows()).thenReturn(Map.of());

    BpmnExporter exporter = new BpmnExporter(registry, generator);

    // When / Then
    assertThatThrownBy(() -> exporter.export("unknown-workflow"))
        .isInstanceOf(WorkflowNotFoundException.class)
        .hasMessageContaining("unknown-workflow");
  }
}
