package cbs.nova.dsl.example.integration;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dslexamples.BatchModels.BatchIn;
import cbs.nova.dslexamples.BatchModels.BatchItem;
import cbs.nova.dslexamples.BatchModels.BatchOut;
import cbs.nova.starter.services.TemporalDslProcessLauncher;
import cbs.nova.starter.services.TemporalDslProcessService;
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

import java.time.Duration;
import java.util.List;

/**
 * End-to-end test that exercises the public service API rather than a generated workflow interface.
 * The flow is:
 *
 * TemporalDslProcessService -> GlobalManager -> ProcessManager -> DefaultProcessRunner ->
 * TemporalProcessLauncher -> Temporal cluster -> generated BatchProcessingProcessDefinition.
 *
 * This keeps the test decoupled from the exact generated workflow interface name.
 */
@Testcontainers
class BatchProcessingDslIntegrationTest {

  private static final String TASK_QUEUE = "BatchProcessing-queue";
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
    GlobalManager.getInstance().resetForTests();
    DslConfig.dslConfig().temporalProcessLauncher().replace(null);

    var globalManager = GlobalManager.getInstance();
    new DefinitionLoader().load(globalManager);
    assertThat(globalManager.hasProcess("BatchProcessing"))
            .as("DSL process BatchProcessing should be loaded")
            .isTrue();
    assertThat(globalManager.hasGeneratedProcess("BatchProcessing"))
            .as("Generated Temporal classes for BatchProcessing should be available")
            .isTrue();

    var serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                    .setTarget(
                            TEMPORAL.getHost() + ":" + TEMPORAL.getMappedPort(7233))
                    .build());
    workflowClient = WorkflowClient.newInstance(serviceStubs);

    var launcher = new TemporalDslProcessLauncher(workflowClient);
    DslConfig.dslConfig().temporalProcessLauncher().replace(launcher);

    var descriptor = globalManager.findGeneratedProcess("BatchProcessing").orElseThrow();
    workerFactory = WorkerFactory.newInstance(workflowClient);
    Worker worker = workerFactory.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(descriptor.temporalImplementation());
    workerFactory.start();
  }

  @AfterAll
  static void tearDown() {
    if (workerFactory != null) {
      workerFactory.shutdown();
    }
    DslConfig.dslConfig().temporalProcessLauncher().replace(null);
  }

  @Test
  void runsBatchProcessingDslThroughServiceApi() {
    var service = new TemporalDslProcessService(new ContextFactory());

    var input = new BatchIn(
            List.of(
                    new BatchItem("a", 1),
                    new BatchItem("b", 2),
                    new BatchItem("c", 3)));

    Result<?> result = service.runProcess("BatchProcessing", input);

    assertThat(result.isSuccess()).isTrue();
    BatchOut out = result.as(BatchOut.class);
    assertThat(out).isNotNull();
    assertThat(out.total()).isEqualTo(6);
    assertThat(out.summary()).isEqualTo("Processed: a=1, b=2, c=3");
  }
}
