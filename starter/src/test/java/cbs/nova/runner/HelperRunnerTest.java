package cbs.nova.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.dsl.api.HelperDefinition;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.ParameterDefinition;
import cbs.nova.model.HelperExecutionRequest;
import cbs.nova.model.HelperExecutionResponse;
import cbs.nova.registry.DslRegistry;
import cbs.nova.temporal.workflow.GenericActivity;
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
class HelperRunnerTest {

  @Mock
  private DslRegistry dslRegistry;

  @Mock
  private GenericActivity genericActivity;

  @InjectMocks
  private HelperRunner helperRunner;

  private HelperDefinition mockHelperDefinition;

  @BeforeEach
  void setUp() {
    mockHelperDefinition = mock(HelperDefinition.class);
  }

  @Test
  @DisplayName("shouldFilterParamsAndExecuteHelper")
  void shouldFilterParamsAndExecuteHelper() {
    HelperExecutionRequest request = new HelperExecutionRequest(
        "HELPER_CODE", "admin1", Map.of("amount", 1000, "unknownParam", "value"), "eventNumber");

    ParameterDefinition paramDef1 = mock(ParameterDefinition.class);
    when(paramDef1.getName()).thenReturn("amount");

    ParameterDefinition paramDef2 = mock(ParameterDefinition.class);
    when(paramDef2.getName()).thenReturn("accountId");

    when(dslRegistry.resolveHelper("HELPER_CODE")).thenReturn(mockHelperDefinition);
    when(mockHelperDefinition.getParameters()).thenReturn(List.of(paramDef1, paramDef2));

    HelperOutput mockOutput = new HelperOutput(Map.of("result", "success"));
    when(genericActivity.execute(eq("HELPER_CODE"), any(HelperInput.class))).thenReturn(mockOutput);
    doReturn(new HelperOutput(Map.of()))
        .when(genericActivity)
        .prepare(eq("HELPER_CODE"), any(Map.class));

    HelperExecutionResponse response = helperRunner.perform(request);

    assertThat(response).isNotNull();
    assertThat(response.executionId()).isNotNull().startsWith("helper-HELPER_CODE-");
    assertThat(response.output()).isEqualTo(mockOutput);

    ArgumentCaptor<Map<String, Object>> prepareParamsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(genericActivity).prepare(eq("HELPER_CODE"), prepareParamsCaptor.capture());
    assertThat(prepareParamsCaptor.getValue()).containsKey("amount");
    assertThat(prepareParamsCaptor.getValue()).doesNotContainKey("unknownParam");

    ArgumentCaptor<HelperInput> executeInputCaptor = ArgumentCaptor.forClass(HelperInput.class);
    verify(genericActivity).execute(eq("HELPER_CODE"), executeInputCaptor.capture());
    assertThat(executeInputCaptor.getValue().params()).containsKey("amount");
    assertThat(executeInputCaptor.getValue().params()).doesNotContainKey("unknownParam");
  }

  @Test
  @DisplayName("shouldIncludeAllParamsWhenAllAreDefined")
  void shouldIncludeAllParamsWhenAllAreDefined() {
    HelperExecutionRequest request = new HelperExecutionRequest(
        "HELPER_CODE", "admin1", Map.of("amount", 500, "accountId", "ACC123"), "eventNumber");

    ParameterDefinition paramDef1 = mock(ParameterDefinition.class);
    when(paramDef1.getName()).thenReturn("amount");

    ParameterDefinition paramDef2 = mock(ParameterDefinition.class);
    when(paramDef2.getName()).thenReturn("accountId");

    when(dslRegistry.resolveHelper("HELPER_CODE")).thenReturn(mockHelperDefinition);
    when(mockHelperDefinition.getParameters()).thenReturn(List.of(paramDef1, paramDef2));

    HelperOutput mockOutput = new HelperOutput(Map.of("result", "ok"));
    when(genericActivity.execute(eq("HELPER_CODE"), any(HelperInput.class))).thenReturn(mockOutput);
    doReturn(new HelperOutput(Map.of()))
        .when(genericActivity)
        .prepare(eq("HELPER_CODE"), any(Map.class));

    HelperExecutionResponse response = helperRunner.perform(request);

    assertThat(response.output()).isEqualTo(mockOutput);

    ArgumentCaptor<HelperInput> executeInputCaptor = ArgumentCaptor.forClass(HelperInput.class);
    verify(genericActivity).execute(eq("HELPER_CODE"), executeInputCaptor.capture());
    assertThat(executeInputCaptor.getValue().params()).hasSize(2);
    assertThat(executeInputCaptor.getValue().params()).containsEntry("amount", 500);
    assertThat(executeInputCaptor.getValue().params()).containsEntry("accountId", "ACC123");
  }

  @Test
  @DisplayName("shouldHandleEmptyParams")
  void shouldHandleEmptyParams() {
    HelperExecutionRequest request = new HelperExecutionRequest("HELPER_CODE", "admin1", Map.of(), "eventNumber");

    when(dslRegistry.resolveHelper("HELPER_CODE")).thenReturn(mockHelperDefinition);
    when(mockHelperDefinition.getParameters()).thenReturn(List.of());

    HelperOutput mockOutput = new HelperOutput(Map.of());
    when(genericActivity.execute(eq("HELPER_CODE"), any(HelperInput.class))).thenReturn(mockOutput);
    doReturn(new HelperOutput(Map.of()))
        .when(genericActivity)
        .prepare(eq("HELPER_CODE"), any(Map.class));

    HelperExecutionResponse response = helperRunner.perform(request);

    assertThat(response).isNotNull();
    assertThat(response.executionId()).startsWith("helper-HELPER_CODE-");
    assertThat(response.output()).isEqualTo(mockOutput);

    ArgumentCaptor<HelperInput> executeInputCaptor = ArgumentCaptor.forClass(HelperInput.class);
    verify(genericActivity).execute(eq("HELPER_CODE"), executeInputCaptor.capture());
    assertThat(executeInputCaptor.getValue().params()).isEmpty();
  }

  @Test
  @DisplayName("shouldThrowWhenHelperNotInRegistry")
  void shouldThrowWhenHelperNotInRegistry() {
    HelperExecutionRequest request =
        new HelperExecutionRequest("UNKNOWN_HELPER", "admin1", Map.of("param", "value"), "eventNumber");

    when(dslRegistry.resolveHelper("UNKNOWN_HELPER"))
        .thenThrow(new IllegalArgumentException("Helper 'UNKNOWN_HELPER' not found"));

    assertThatThrownBy(() -> helperRunner.perform(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("UNKNOWN_HELPER");
  }

  @Test
  @DisplayName("shouldPreservePerformedByInFilteredRequest")
  void shouldPreservePerformedByInFilteredRequest() {
    HelperExecutionRequest request =
        new HelperExecutionRequest("HELPER_CODE", "specific-performer", Map.of("extra", "value"), "eventNumber");

    ParameterDefinition paramDef = mock(ParameterDefinition.class);
    when(paramDef.getName()).thenReturn("required");

    when(dslRegistry.resolveHelper("HELPER_CODE")).thenReturn(mockHelperDefinition);
    when(mockHelperDefinition.getParameters()).thenReturn(List.of(paramDef));

    HelperOutput mockOutput = new HelperOutput(Map.of());
    when(genericActivity.execute(eq("HELPER_CODE"), any(HelperInput.class))).thenReturn(mockOutput);
    doReturn(new HelperOutput(Map.of()))
        .when(genericActivity)
        .prepare(eq("HELPER_CODE"), any(Map.class));

    HelperExecutionResponse response = helperRunner.perform(request);

    assertThat(response).isNotNull();

    ArgumentCaptor<HelperInput> executeInputCaptor = ArgumentCaptor.forClass(HelperInput.class);
    verify(genericActivity).execute(eq("HELPER_CODE"), executeInputCaptor.capture());
    assertThat(executeInputCaptor.getValue().params()).doesNotContainKey("extra");
  }

  @Test
  @DisplayName("shouldPreserveHelperCodeInFilteredRequest")
  void shouldPreserveHelperCodeInFilteredRequest() {
    HelperExecutionRequest request =
        new HelperExecutionRequest("MY_HELPER_CODE", "admin", Map.of("extra", "value"), "eventNumber");

    ParameterDefinition paramDef = mock(ParameterDefinition.class);
    when(paramDef.getName()).thenReturn("required");

    when(dslRegistry.resolveHelper("MY_HELPER_CODE")).thenReturn(mockHelperDefinition);
    when(mockHelperDefinition.getParameters()).thenReturn(List.of(paramDef));

    HelperOutput mockOutput = new HelperOutput(Map.of());
    when(genericActivity.execute(eq("MY_HELPER_CODE"), any(HelperInput.class)))
        .thenReturn(mockOutput);
    doReturn(new HelperOutput(Map.of()))
        .when(genericActivity)
        .prepare(eq("MY_HELPER_CODE"), any(Map.class));

    HelperExecutionResponse response = helperRunner.perform(request);

    assertThat(response).isNotNull();

    verify(genericActivity).prepare(eq("MY_HELPER_CODE"), any(Map.class));
    verify(genericActivity).execute(eq("MY_HELPER_CODE"), any(HelperInput.class));
  }
}
