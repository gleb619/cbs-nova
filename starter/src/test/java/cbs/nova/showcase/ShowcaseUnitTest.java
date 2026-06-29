package cbs.nova.showcase;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.nova.model.HelperExecutionRequest;
import cbs.nova.model.HelperExecutionResponse;
import cbs.nova.runner.HelperRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit-style tests that verify the DSL → generated definition → execution chain
 * with a local {@link DslRegistry} and a real {@link cbs.dsl.evaluator.Evaluator}.
 */
class ShowcaseUnitTest extends ShowcaseTestBase {

  @Test
  @DisplayName("Should compile DSL files in Gradle container and register generated definitions")
  void shouldCompileDslInGradleContainerAndRegisterDefinitions() {
    assertThat(dslRegistry.getHelpers()).containsKey("SAMPLE_HELPER_VIA_DSL");
    assertThat(dslRegistry.getEvents()).containsKey("SAMPLE_EVENT_VIA_DSL");
    assertThat(dslRegistry.getTransactions()).containsKey("SAMPLE_TRANSACTION_VIA_DSL");
    assertThat(dslRegistry.getWorkflows()).containsKey("DSL_TEST_WF");
  }

  @Test
  @DisplayName("Should run DSL helper via HelperRunner")
  // Must stay in sync with starter/src/test/resources/dsl/sample1/SampleHelperDsl.java.
  // The helper enriches "name" through SAMPLE_HELPER, then stores the result under "someValue".
  void shouldRunDslHelperViaHelperRunnerWithMockedActivity() {
    HelperRunner helperRunner = new HelperRunner(dslRegistry);

    Map<String, Object> params = new HashMap<>();
    params.put("name", "Alice");
    params.put("undefinedParam", "should be filtered out");

    HelperExecutionRequest request = HelperExecutionRequest.builder()
        .helperCode("SAMPLE_HELPER_VIA_DSL")
        .performedBy("tester")
        .params(params)
        .build();

    HelperOutput expectedOutput = new HelperOutput(Map.of("someValue", "Hello, Alice"));

    HelperExecutionResponse response = helperRunner.perform(request);

    assertThat(response).isNotNull();
    assertThat(response.executionId()).startsWith("helper-SAMPLE_HELPER_VIA_DSL-");
    assertThat(response.output()).isEqualTo(expectedOutput);
    assertThat(response.output().params()).containsOnlyKeys("someValue");
    assertThat(params).doesNotContainKey("undefinedParam");
  }
}
