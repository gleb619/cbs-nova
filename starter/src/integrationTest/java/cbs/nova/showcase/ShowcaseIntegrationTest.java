package cbs.nova.showcase;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.context.EnrichmentContext;
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
 * Integration test that verifies the full DSL → compile → registry → Temporal execution chain
 * using an in-memory real Temporal server via {@link TestWorkflowEnvironment}.
 */
class ShowcaseIntegrationTest extends ShowcaseITBase {

  private TestWorkflowEnvironment testEnv;
  private WorkflowClient workflowClient;

  @BeforeEach
  void setUpTemporal() {
    testEnv = TestWorkflowEnvironment.newInstance();
    Worker worker = testEnv.newWorker("TEST_TASK_QUEUE");
    worker.registerWorkflowImplementationTypes(
        ShowcaseTxWorkflowImpl.class,
        ShowcaseCtxWorkflowImpl.class);
    worker.registerActivitiesImplementations(new ShowcaseTestActivityImpl(dslRegistry));
    testEnv.start();
    workflowClient = testEnv.getWorkflowClient();
  }

  @AfterEach
  void tearDownTemporal() {
    if (testEnv != null) {
      testEnv.shutdown();
    }
  }

  @Test
  @DisplayName("Should execute DSL transaction through real Temporal workflow")
  void shouldExecuteDslTransactionThroughRealTemporalWorkflow() {
    ShowcaseTxWorkflow workflow = workflowClient.newWorkflowStub(
        ShowcaseTxWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("TEST_TASK_QUEUE")
            .setWorkflowId("showcase-test-tx")
            .build());

    //TODO: We need a new service/method at
    WorkflowExecutionResponse response = workflow.execute(
        new EventWorkflowRequest("DSL_TEST_WF", "SAMPLE_EVENT_DSL", "{}", "testUser", "dev", null));

    assertThat(response).isNotNull();
    assertThat(response.executionId()).isEqualTo(1L);
    assertThat(response.status()).isEqualTo("DSL TX says hello to PoC");
  }

  @Test
  @DisplayName("Should execute DSL event context through real Temporal activity")
  void shouldExecuteDslEventContextThroughRealTemporalActivity() {
    ShowcaseCtxWorkflow workflow = workflowClient.newWorkflowStub(
        ShowcaseCtxWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("TEST_TASK_QUEUE")
            .setWorkflowId("showcase-test-ctx")
            .build());

    WorkflowExecutionResponse response = workflow.execute(
        new EventWorkflowRequest("DSL_TEST_WF", "SAMPLE_EVENT_DSL", "{}", "testUser", "dev", null));

    assertThat(response).isNotNull();
    assertThat(response.executionId()).isEqualTo(1L);
    assertThat(response.status()).isEqualTo("enriched");
  }

  /* ============= */

  //TODO: next classes must be codegenerated at `dsl-codegen/src/main/java/cbs/dsl/codegen/DslCodeGenerator.java` and
  // not hardcoded in test itself.

  @WorkflowInterface
  public interface ShowcaseTxWorkflow {
    @WorkflowMethod
    WorkflowExecutionResponse execute(EventWorkflowRequest input);
  }

  @WorkflowInterface
  public interface ShowcaseCtxWorkflow {
    @WorkflowMethod
    WorkflowExecutionResponse execute(EventWorkflowRequest input);
  }

  public static class ShowcaseTxWorkflowImpl implements ShowcaseTxWorkflow {

    private final ShowcaseTestActivity activity = Workflow.newActivityStub(
        ShowcaseTestActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(5))
            .build());

    @Override
    public WorkflowExecutionResponse execute(EventWorkflowRequest input) {
      return activity.runDslTransaction(input);
    }
  }

  public static class ShowcaseCtxWorkflowImpl implements ShowcaseCtxWorkflow {

    private final ShowcaseTestActivity activity = Workflow.newActivityStub(
        ShowcaseTestActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(5))
            .build());

    @Override
    public WorkflowExecutionResponse execute(EventWorkflowRequest input) {
      return activity.runDslContext(input);
    }
  }

  @ActivityInterface
  public interface ShowcaseTestActivity {

    @ActivityMethod
    WorkflowExecutionResponse runDslTransaction(EventWorkflowRequest input);

    @ActivityMethod
    WorkflowExecutionResponse runDslContext(EventWorkflowRequest input);

  }

  //TODO: For current flow, our business entity `Event` must be mapped to Temporal `Workflow`
  // For business entity `Transaction` must be mapped to Temporal `Activity`
  // For `Transaction` we just need to generate code for sequential code execution, for each parameter(e.g. a context
  // closure evaluation) and pass to `TransactionFunction`(dsl-api/src/main/java/cbs/dsl/api/TransactionFunction.java)
  public static class ShowcaseTestActivityImpl implements ShowcaseTestActivity {

    private final DslRegistry dslRegistry;

    public ShowcaseTestActivityImpl(DslRegistry dslRegistry) {
      this.dslRegistry = dslRegistry;
    }

    @Override
    public WorkflowExecutionResponse runDslTransaction(EventWorkflowRequest input) {
      TransactionDefinition txDef = dslRegistry.resolveTransaction("SAMPLE_TRANSACTION_DSL");
      var txInput = new TransactionInput(
          Map.of("name", "PoC"), "SAMPLE_TRANSACTION_DSL", null, "dev");
      var output = txDef.execute(txInput);
      return new WorkflowExecutionResponse(1L, (String) output.result().get("greeting"));
    }

    @Override
    public WorkflowExecutionResponse runDslContext(EventWorkflowRequest input) {
      EventDefinition eventDef = dslRegistry.resolveEvent("SAMPLE_EVENT_DSL");
      EnrichmentContext ctx = new EnrichmentContext(
          "SAMPLE_EVENT_DSL", 0L, "testUser", "dev", Map.of("name", "PoC"));
      ctx.setHelperResolver((name, params) -> dslRegistry
          .resolveHelper(name)
          .execute(new HelperInput(params, "SAMPLE_EVENT_DSL", null))
          .value());
      eventDef.getContextBlock().accept(ctx);
      return new WorkflowExecutionResponse(
          1L, ctx.getEnrichment().containsKey("enriched") ? "enriched" : "not-enriched");
    }
  }
}
