package cbs.nova.showcase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.nova.model.HelperExecutionRequest;
import cbs.nova.model.HelperExecutionResponse;
import cbs.nova.runner.HelperRunner;
import cbs.nova.temporal.workflow.GenericActivity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit-style tests that verify the DSL → registry → execution chain with a mocked Temporal client.
 */
class ShowcaseUnitTest extends ShowcaseTestBase {

  @Test
  @DisplayName("Should compile DSL files in Gradle container and register definitions")
  void shouldCompileDslInGradleContainerAndRegisterDefinitions() {
    assertThat(dslRegistry.getHelpers()).containsKey("SAMPLE_HELPER_VIA_DSL");
    assertThat(dslRegistry.getEvents()).containsKey("SAMPLE_EVENT_VIA_DSL");
    assertThat(dslRegistry.getTransactions()).containsKey("SAMPLE_TRANSACTION_VIA_DSL");
    assertThat(dslRegistry.getWorkflows()).containsKey("DSL_TEST_WF");
  }

  @Test
  @DisplayName("Should run DSL helper via HelperRunner with mocked Temporal activity")
  void shouldRunDslHelperViaHelperRunnerWithMockedActivity() {
    GenericActivity genericActivity = mock(GenericActivity.class);
    HelperRunner helperRunner = new HelperRunner(dslRegistry, genericActivity);

    Map<String, Object> params = new HashMap<>();
    params.put("name", "Alice");
    params.put("undefinedParam", "should be filtered out");

    HelperExecutionRequest request = HelperExecutionRequest.builder()
        .helperCode("SAMPLE_HELPER_VIA_DSL")
        .performedBy("tester")
        .params(params)
        .build();

    HelperOutput expectedOutput = new HelperOutput(Map.of("someValue", "Hello, Alice"));
    when(genericActivity.execute(eq("SAMPLE_HELPER_VIA_DSL"), any(HelperInput.class)))
        .thenReturn(expectedOutput);

    HelperExecutionResponse response = helperRunner.perform(request);

    assertThat(response).isNotNull();
    assertThat(response.executionId()).startsWith("helper-SAMPLE_HELPER_VIA_DSL-");
    assertThat(response.output()).isEqualTo(expectedOutput);

    verify(genericActivity)
        .prepare(
            eq("SAMPLE_HELPER_VIA_DSL"),
            argThat(preparedParams -> preparedParams instanceof Map
                && preparedParams.containsKey("name")
                && !preparedParams.containsKey("undefinedParam")
                && "Alice".equals(((Map<?, ?>) preparedParams).get("name"))));

    verify(genericActivity)
        .execute(
            eq("SAMPLE_HELPER_VIA_DSL"),
            argThat(input -> input instanceof HelperInput
                && input.params().containsKey("name")
                && !input.params().containsKey("undefinedParam")
                && "Alice".equals(input.params().get("name"))));
  }
}
