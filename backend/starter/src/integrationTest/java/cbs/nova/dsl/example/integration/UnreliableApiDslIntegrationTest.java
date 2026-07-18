package cbs.nova.dsl.example.integration;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.HelperInstanceResolver;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.dslexamples.UnreliableApiModels.UnreliableProcessIn;
import cbs.nova.dslexamples.UnreliableApiModels.UnreliableProcessOut;
import cbs.nova.starter.helpers.CompensationTrackerHelper;
import cbs.nova.starter.helpers.model.UnreliableApiIn;
import cbs.nova.starter.services.TemporalDslProcessLauncher;
import cbs.nova.starter.services.TemporalDslProcessService;
import cbs.nova.starter.services.TemporalTransactionInvoker;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * End-to-end test for Temporal retry, backoff, and compensation behavior using an unreliable helper
 * that fails a configured number of times.
 */
@Testcontainers
class UnreliableApiDslIntegrationTest {

  private static final String TASK_QUEUE = "unreliable-api-queue";
  private static final DockerImageName TEMPORAL_IMAGE = DockerImageName
          .parse("temporalio/auto-setup:1.25.2");
  private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:15");

  private static final Network NETWORK = Network.newNetwork();

  @Container
  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
          .withNetwork(NETWORK)
          .withNetworkAliases("postgres")
          .withDatabaseName("temporal")
          .withUsername("temporal")
          .withPassword("temporal");

  @Container
  private static final GenericContainer<?> TEMPORAL = new GenericContainer<>(TEMPORAL_IMAGE)
          .withNetwork(NETWORK)
          .withExposedPorts(7233)
          .withEnv("DB", "postgres12")
          .withEnv("DB_PORT", "5432")
          .withEnv("POSTGRES_USER", "temporal")
          .withEnv("POSTGRES_PWD", "temporal")
          .withEnv("POSTGRES_SEEDS", "postgres")
          .dependsOn(POSTGRES)
          .waitingFor(
                  new WaitAllStrategy()
                          .withStrategy(Wait.forListeningPort())
                          .withStrategy(
                                  Wait.forLogMessage(".*Namespace cache refreshed.*", 1))
                          .withStartupTimeout(Duration.ofMinutes(5)));

  private static WorkerFactory workerFactory;
  private static WorkflowClient workflowClient;

  @BeforeAll
  static void setUp() {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().temporalProcessLauncher().replace(null);

    var globalManager = GlobalManager.globalManager();
    new DefinitionLoader().load(globalManager);
    DslConfig.dslConfig().helperInstanceResolver().replace(reflectiveHelperResolver());
    globalManager.registerHelperResolvers();

    assertThat(globalManager.hasProcess("UnreliableApiSuccess")).isTrue();
    assertThat(globalManager.hasProcess("UnreliableApiCompensated")).isTrue();
    assertThat(globalManager.hasProcess("UnreliableApiUncaught")).isTrue();
    assertThat(globalManager.hasTransaction("unreliableApiTxResilient")).isTrue();
    assertThat(globalManager.hasTransaction("unreliableApiTxFragile")).isTrue();

    var serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                    .setTarget(
                            TEMPORAL.getHost() + ":" + TEMPORAL.getMappedPort(7233))
                    .build());
    workflowClient = WorkflowClient.newInstance(serviceStubs);

    var launcher = new TemporalDslProcessLauncher(workflowClient, new ObjectMapper());
    DslConfig.dslConfig().temporalProcessLauncher().replace(launcher);
    DslConfig.dslConfig().transactionInvoker().replace(new TemporalTransactionInvoker());

    workerFactory = WorkerFactory.newInstance(workflowClient);
    Worker worker = workerFactory.newWorker(TASK_QUEUE);
    registerProcess(worker, "UnreliableApiSuccess");
    registerProcess(worker, "UnreliableApiCompensated");
    registerProcess(worker, "UnreliableApiUncaught");
    registerTransaction(worker, "unreliableApiTxResilient");
    registerTransaction(worker, "unreliableApiTxFragile");
    workerFactory.start();
  }

  private static void registerProcess(Worker worker, String name) {
    var descriptor = GlobalManager.globalManager().findGeneratedProcess(name).orElseThrow();
    worker.registerWorkflowImplementationTypes(descriptor.temporalImplementation());
  }

  private static void registerTransaction(Worker worker, String name) {
    var descriptor = GlobalManager.globalManager().findGeneratedTransaction(name).orElseThrow();
    Object instance;
    try {
      //TODO: remove reflection, use typed info instead
      instance = descriptor.temporalImplementation().getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Failed to instantiate activity " + name, e);
    }
    worker.registerActivitiesImplementations(instance);
  }

  @AfterAll
  static void tearDown() {
    if (workerFactory != null) {
      workerFactory.shutdown();
    }
    DslConfig.dslConfig().temporalProcessLauncher().replace(null);
    DslConfig.dslConfig().transactionInvoker().replace(null);
    DslConfig.dslConfig().helperInstanceResolver().replace(null);
  }

  @Test
  void resilientTransactionSucceedsAfterTemporalRetries() {
    var service = new TemporalDslProcessService(
            new ContextFactory(), new InMemoryDslRunRepository(), new ObjectMapper());
    String runId = "unreliable-success-" + System.currentTimeMillis();
    var apiCall = new UnreliableApiIn(runId, 3, false, null);
    var input = new UnreliableProcessIn("success", apiCall);

    Result<?> result = service.runProcess("UnreliableApiSuccess", input);

    assertThat(result.isSuccess())
            .as("success result cause: %s", result.cause())
            .isTrue();
    UnreliableProcessOut out = result.as(UnreliableProcessOut.class);
    assertThat(out).isNotNull();
    assertThat(out.status()).isEqualTo("SUCCESS");
  }

  @Test
  void fragileTransactionFailsAndProcessCompensates() {
    var tracker = tracker();
    String runId = "unreliable-compensated-" + System.currentTimeMillis();
    var apiCall = new UnreliableApiIn(runId, 5, false, null);
    var input = new UnreliableProcessIn("compensated", apiCall);
    String markerId = "UnreliableApiCompensated-" + input.scenario();

    Result<?> result = new TemporalDslProcessService(
            new ContextFactory(), new InMemoryDslRunRepository(), new ObjectMapper())
            .runProcess("UnreliableApiCompensated", input);

    assertThat(result.isSuccess()).isFalse();
    assertThat(tracker.wasCompensated(markerId)).isTrue();
  }

  @Test
  void fragileTransactionFailsWithoutCompensationWhenNoneConfigured() {
    var tracker = tracker();
    String runId = "unreliable-uncaught-" + System.currentTimeMillis();
    String markerId = "UnreliableApiCompensated-" + runId;
    var apiCall = new UnreliableApiIn(runId, 5, false, null);
    var input = new UnreliableProcessIn("uncaught", apiCall);

    Result<?> result = new TemporalDslProcessService(
            new ContextFactory(), new InMemoryDslRunRepository(), new ObjectMapper())
            .runProcess("UnreliableApiUncaught", input);

    assertThat(result.isSuccess()).isFalse();
    assertThat(tracker.wasCompensated(markerId)).isFalse();
  }

  private static CompensationTrackerHelper tracker() {
    return GlobalManager.globalManager().findHelper("compensationTracker")
            .map(CompensationTrackerHelper.class::cast)
            .orElseThrow(
                    () -> new IllegalStateException("compensationTracker helper not registered"));
  }

  private static HelperInstanceResolver reflectiveHelperResolver() {
    return helperClass -> {
      try {
        //TODO: remove reflection, use typed info instead
        return (Executable<?, ?>) helperClass.getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot instantiate helper " + helperClass, e);
      }
    };
  }
}
