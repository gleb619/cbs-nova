package cbs.nova.showcase;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.nova.model.EventWorkflowRequest;
import cbs.nova.model.WorkflowExecutionResponse;
import cbs.nova.registry.DslRegistry;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

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

  //TODO: Write new IntegrationTests for DSL with full temporal support

}
