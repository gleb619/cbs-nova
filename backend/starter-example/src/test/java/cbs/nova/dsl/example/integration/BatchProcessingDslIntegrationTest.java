package cbs.nova.dsl.example.integration;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.generated.batchprocessing.v1.BatchProcessingProcessDefinition;
import cbs.nova.dsl.generated.batchprocessing.v1.BatchProcessingProcessWorkflow;
import cbs.nova.dslmodel.BatchIn;
import cbs.nova.dslmodel.BatchItem;
import cbs.nova.dslmodel.BatchOut;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

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
                  Wait.forLogMessage(".*Started Worker.*", 1)
                          .withStartupTimeout(Duration.ofMinutes(5)));

  private static WorkerFactory workerFactory;
  private static WorkflowClient workflowClient;

  @BeforeAll
  static void setUp() {
    GlobalManager.getInstance().resetForTests();
    var globalManager = GlobalManager.getInstance();
    var dslSourceDir = Path.of(System.getProperty("dsl.examples.src.dir"));
    new DefinitionLoader().load(dslSourceDir, globalManager);
    assertThat(globalManager.hasProcess("BatchProcessing"))
            .as("DSL source for BatchProcessing should be loaded")
            .isTrue();

    var serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                    .setTarget(
                            TEMPORAL.getHost() + ":" + TEMPORAL.getMappedPort(7233))
                    .build());
    workflowClient = WorkflowClient.newInstance(serviceStubs);

    workerFactory = WorkerFactory.newInstance(workflowClient);
    Worker worker = workerFactory.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(BatchProcessingProcessDefinition.class);
    workerFactory.start();
  }

  @AfterAll
  static void tearDown() {
    if (workerFactory != null) {
      workerFactory.shutdown();
    }
  }

  @Test
  void runsBatchProcessingDslEndToEnd() {
    var workflow = workflowClient.newWorkflowStub(
            BatchProcessingProcessWorkflow.class,
            WorkflowOptions.newBuilder()
                    .setTaskQueue(TASK_QUEUE)
                    .setWorkflowId("batch-processing-test-1")
                    .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                    .setWorkflowTaskTimeout(Duration.ofSeconds(5))
                    .build());

    var input = new BatchIn(
            List.of(
                    new BatchItem("a", 1),
                    new BatchItem("b", 2),
                    new BatchItem("c", 3)));

    BatchOut result = workflow.run(input);

    assertThat(result.total()).isEqualTo(6);
    assertThat(result.summary()).isEqualTo("Processed: a=1, b=2, c=3");
  }
}
