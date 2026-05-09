package cbs.nova.showcase;

import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import org.junit.jupiter.api.AfterEach;

/**
 * Integration test that verifies the full DSL → compile → registry → Temporal execution chain using
 * an in-memory real Temporal server via {@link TestWorkflowEnvironment}.
 */
class ShowcaseIntegrationTest extends ShowcaseITBase {

  private TestWorkflowEnvironment testEnv;
  private WorkflowClient workflowClient;

  @AfterEach
  void tearDownTemporal() {
    if (testEnv != null) {
      testEnv.shutdown();
    }
  }

  // TODO: Write new IntegrationTests for DSL with full temporal support

}
