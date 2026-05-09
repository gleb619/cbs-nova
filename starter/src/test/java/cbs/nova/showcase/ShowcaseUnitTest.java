package cbs.nova.showcase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-style tests that verify the DSL → registry → execution chain with a mocked Temporal client.
 */
class ShowcaseUnitTest extends ShowcaseTestBase {

  @Test
  @DisplayName("Should compile DSL files in Gradle container and register definitions")
  void shouldCompileDslInGradleContainerAndRegisterDefinitions() {
    assertThat(dslRegistry.getEvents()).containsKey("SAMPLE_EVENT_DSL");
    assertThat(dslRegistry.getTransactions()).containsKey("SAMPLE_TRANSACTION_DSL");
    assertThat(dslRegistry.getWorkflows()).containsKey("DSL_TEST_WF");
  }

  // TODO: Write new UnitTests for DSL with Temporal mock

}
