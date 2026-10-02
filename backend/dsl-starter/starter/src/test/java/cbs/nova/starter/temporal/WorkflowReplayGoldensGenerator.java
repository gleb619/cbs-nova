package cbs.nova.starter.temporal;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.process.DslTemporalProcessRequest;
import cbs.nova.dslexamples.v1.SampleProcessProcessWorkflow;
import cbs.nova.dslexamples.v1.SampleProcessProcessDefinition;
import cbs.nova.dslexamples.v1.SimpleOrderProcessWorkflow;
import cbs.nova.dslexamples.v1.SimpleOrderProcessDefinition;
import cbs.nova.dslexamples.v1.SignalBranchProcessWorkflow;
import cbs.nova.dslexamples.v1.SignalBranchProcessDefinition;
import cbs.nova.dslexamples.v1.SignalBranchModels.SignalBranchIn;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.utils.DefinitionLoader;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.BatchRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.workflow.Functions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * One-off generator that captures workflow histories for the three representative DSL examples used
 * by {@link WorkflowReplayGuardTest} as golden histories. Runs only when the system property
 * {@code cbs.nova.t770.regenerate-goldens=true} is set; otherwise it is skipped so the regular test
 * suite stays fast and the golden files are treated as immutable inputs.
 *
 * <p>
 * The captured JSON files become the regression baseline: any codegen or runtime change that
 * silently alters deterministic command order would surface as a {@code NonDeterministicException}
 * the next time {@link WorkflowReplayGuardTest} replays one of them. Regenerate only when a change
 * is intentionally replay-incompatible, and call it out in the commit message.
 */
@EnabledIfSystemProperty(named = "cbs.nova.t770.regenerate-goldens", matches = "true")
class WorkflowReplayGoldensGenerator {

  private static final String TASK_QUEUE = "t770-replay";
  private static final Path GOLDENS_DIR = Path.of(
          "src/test/resources/replay");

  private TestWorkflowEnvironment env;
  private WorkflowClient client;

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    new DefinitionLoader().load(GlobalManager.globalManager());

    env = TestWorkflowEnvironment.newInstance();
    client = env.getWorkflowClient();
  }

  @AfterEach
  void tearDown() {
    if (env != null) {
      env.close();
    }
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void captureSampleProcess() throws Exception {
    var worker = env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(SampleProcessProcessDefinition.class);
    startEnv();

    WorkflowOptions options = baseOptions("sample-process");
    SampleProcessProcessWorkflow typed = client.newWorkflowStub(
            SampleProcessProcessWorkflow.class, options);
    WorkflowExecution execution = WorkflowClient.start(
            (Functions.Func1<DslTemporalProcessRequest<String>, Object>) typed::execute,
            new DslTemporalProcessRequest<>("rid-sample", "hello-dsl"));
    // Block until the workflow task is processed and the completion event is appended to history.
    env.sleep(Duration.ofSeconds(1));

    captureHistory(execution, "SampleProcess.json");
  }

  @Test
  void captureSimpleOrder() throws Exception {
    var worker = env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(SimpleOrderProcessDefinition.class);
    startEnv();

    WorkflowOptions options = baseOptions("simple-order");
    SimpleOrderProcessWorkflow typed = client.newWorkflowStub(
            SimpleOrderProcessWorkflow.class, options);
    WorkflowExecution execution = WorkflowClient.start(
            (Functions.Func1<DslTemporalProcessRequest<MapInput>, Object>) typed::execute,
            new DslTemporalProcessRequest<>("rid-order",
                    MapInput.of("orderId", "ord-42")));
    env.sleep(Duration.ofSeconds(1));

    captureHistory(execution, "SimpleOrder.json");
  }

  @Test
  void captureSignalBranch() throws Exception {
    var worker = env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(SignalBranchProcessDefinition.class);
    startEnv();

    WorkflowOptions options = baseOptions("signal-branch");
    SignalBranchProcessWorkflow typed = client.newWorkflowStub(
            SignalBranchProcessWorkflow.class, options);
    SignalBranchIn payload = new SignalBranchIn("req-77", 250);

    BatchRequest request = client.newSignalWithStartRequest();
    request.add((Functions.Func1<DslTemporalProcessRequest<SignalBranchIn>, Object>) typed::execute,
            new DslTemporalProcessRequest<>("rid-signal", payload));
    request.add((Functions.Proc1<Map<String, Object>>) typed::decision,
            Map.of("approved", true));
    WorkflowExecution execution = client.signalWithStart(request);

    env.sleep(Duration.ofSeconds(1));

    captureHistory(execution, "SignalBranch.json");
  }

  @Test
  void captureReplayMismatchCanonical() throws Exception {
    var worker = env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(
            ReplayMismatchWorkflow.CanonicalImpl.class);
    startEnv();

    WorkflowOptions options = baseOptions("replay-mismatch-canonical");
    ReplayMismatchWorkflow.ReplayMismatchWorkflowIface typed = client.newWorkflowStub(
            ReplayMismatchWorkflow.ReplayMismatchWorkflowIface.class, options);
    WorkflowExecution execution = WorkflowClient.start(typed::execute);
    env.sleep(Duration.ofSeconds(1));

    captureHistory(execution, "ReplayMismatchCanonical.json");
  }

  private void startEnv() {
    if (!env.isStarted()) {
      env.start();
    }
  }

  private void captureHistory(WorkflowExecution execution, String fileName)
          throws IOException {
    var execHistory = env.getWorkflowExecutionHistory(execution);
    String json = execHistory.toJson(true);
    Files.createDirectories(GOLDENS_DIR);
    Files.writeString(GOLDENS_DIR.resolve(fileName), json);
  }

  private static WorkflowOptions baseOptions(String workflowId) {
    return WorkflowOptions.newBuilder()
            .setTaskQueue(TASK_QUEUE)
            .setWorkflowId(workflowId)
            .setWorkflowExecutionTimeout(Duration.ofMinutes(1))
            .setWorkflowTaskTimeout(Duration.ofSeconds(10))
            .build();
  }
}
