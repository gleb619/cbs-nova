package cbs.nova.dsl.example.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.DslTemporalProcessRequest;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.HelperInstanceResolver;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.dslexamples.VersionProbeModels.VersionProbeIn;
import cbs.nova.dslexamples.VersionProbeModels.VersionProbeOut;
import cbs.nova.dslexamples.versionprobe.v1.VersionProbeProcessWorkflow;
import cbs.nova.starter.helpers.*;
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

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that a running workflow keeps using the DSL version it started with after a newer DSL
 * version is loaded, while a workflow started after the reload uses the new version. A file-based
 * latch helper keeps the first execution open while the registry is updated; after the latch file
 * is released the workflow still finishes with the v1 logic.
 */
@Testcontainers
class DslVersioningIntegrationTest {

  private static final String TASK_QUEUE = "VersionProbe-queue";
  private static final Path LATCH_DIR = Path
          .of(System.getProperty("java.io.tmpdir"), "cbs-nova-versioning-latch");
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
  static void setUp() throws IOException {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().temporalProcessLauncher().replace(null);
    cleanLatchDir();

    var globalManager = GlobalManager.globalManager();
    new DefinitionLoader().load(globalManager);
    DslConfig.dslConfig().helperInstanceResolver().replace(typedHelperResolver());
    globalManager.registerHelperResolvers();

    assertThat(globalManager.hasProcess("VersionProbe"))
            .as("DSL process VersionProbe v1 should be loaded")
            .isTrue();
    assertThat(globalManager.findProcess("VersionProbe").orElseThrow().version())
            .isEqualTo("v1");
    assertThat(globalManager.hasGeneratedProcess("VersionProbe"))
            .as("Generated Temporal classes for VersionProbe should be available")
            .isTrue();

    var serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                    .setTarget(
                            TEMPORAL.getHost() + ":" + TEMPORAL.getMappedPort(7233))
                    .build());
    workflowClient = WorkflowClient.newInstance(serviceStubs);

    var launcher = new TemporalDslProcessLauncher(workflowClient, new ObjectMapper(),
            Duration.ofSeconds(30), Duration.ofSeconds(5));
    DslConfig.dslConfig().temporalProcessLauncher().replace(launcher);
    DslConfig.dslConfig().transactionInvoker().replace(new TemporalTransactionInvoker());

    var descriptor = globalManager.findGeneratedProcess("VersionProbe").orElseThrow();
    workerFactory = WorkerFactory.newInstance(workflowClient);
    Worker worker = workerFactory.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(descriptor.temporalImplementation());
    workerFactory.start();
  }

  @AfterAll
  static void tearDown() throws IOException {
    if (workerFactory != null) {
      workerFactory.shutdown();
    }
    DslConfig.dslConfig().temporalProcessLauncher().replace(null);
    DslConfig.dslConfig().transactionInvoker().replace(null);
    DslConfig.dslConfig().helperInstanceResolver().replace(null);
    cleanLatchDir();
  }

  private static void cleanLatchDir() throws IOException {
    if (!Files.exists(LATCH_DIR)) {
      return;
    }
    try (var stream = Files.list(LATCH_DIR)) {
      stream.forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }
  }

  @Test
  void inFlightWorkflowKeepsUsingOriginalDslVersionAfterReload() throws Exception {
    var service = new TemporalDslProcessService(new ContextFactory(),
            new InMemoryDslRunRepository(), new ObjectMapper());

    var firstRun = service.startProcess("VersionProbe", new VersionProbeIn("first"));

    Path lock = LATCH_DIR.resolve("lock-" + firstRun.runId());
    await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(50))
            .until(() -> Files.exists(lock));

    Path v2Dir = Path.of("src/integrationTest/resources/dsl-versioning-v2");
    new DefinitionLoader().load(v2Dir, GlobalManager.globalManager());

    assertThat(GlobalManager.globalManager().findProcess("VersionProbe").orElseThrow().version())
            .as("latest registered DSL version should be v2 after reload")
            .isEqualTo("v2");

    Path release = LATCH_DIR.resolve("release-" + firstRun.runId());
    Files.writeString(release, "go");

    Result<?> firstResult = firstRun.result().get(30, TimeUnit.SECONDS);
    assertThat(firstResult.isSuccess()).as("result cause: %s", firstResult.cause()).isTrue();
    VersionProbeOut firstOut = firstResult.as(VersionProbeOut.class);

    assertThat(firstOut).isNotNull();
    assertThat(firstOut.result()).isEqualTo("v1:first");

    // First workflow finished with v1. Switch the worker to the v2 implementation and start a
    // second workflow; it must use the freshly loaded v2 DSL and produce a different result.
    workerFactory.shutdown();
    workerFactory = WorkerFactory.newInstance(workflowClient);
    Worker worker = workerFactory.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(VersionProbeV2ProcessDefinition.class);
    workerFactory.start();

    GlobalManager.globalManager().registerGeneratedClass(
            new GeneratedClassDescriptor(
                    "VersionProbe",
                    DslObject.DslType.PROCESS,
                    "v2",
                    TASK_QUEUE,
                    VersionProbeProcessWorkflow.class,
                    VersionProbeV2ProcessDefinition.class,
                    VersionProbeIn.class,
                    VersionProbeOut.class,
                    "{}"));

    var secondRun = service.startProcess("VersionProbe", new VersionProbeIn("second"));
    Result<?> secondResult = secondRun.result().get(30, TimeUnit.SECONDS);
    assertThat(secondResult.isSuccess()).as("result cause: %s", secondResult.cause()).isTrue();
    VersionProbeOut secondOut = secondResult.as(VersionProbeOut.class);

    assertThat(secondOut).isNotNull();
    assertThat(secondOut.result()).isEqualTo("v2:second");
    assertThat(secondOut.result())
            .as("second workflow should use the reloaded DSL version")
            .isNotEqualTo(firstOut.result());
  }

  public static final class VersionProbeV2ProcessDefinition implements VersionProbeProcessWorkflow {

    @Override
    public String getVersion() {
      return "v2";
    }

    @Override
    public Object execute(DslTemporalProcessRequest<VersionProbeIn> request) {
      return GlobalManager.globalManager().runProcessWithCompensation(
              request.runId(),
              request.payload(),
              ctx -> GlobalManager.globalManager().runProcess("VersionProbe", "v2", ctx),
              (compCtx, error) -> GlobalManager.globalManager()
                      .compensateProcess("VersionProbe", compCtx, error));
    }
  }

  private static HelperInstanceResolver typedHelperResolver() {
    return helperClass -> {
      if (helperClass == ConditionalFailingHelper.class) {
        return new ConditionalFailingHelper();
      }
      if (helperClass == CompensationTrackerHelper.class) {
        return new CompensationTrackerHelper();
      }
      if (helperClass == CurrentTimestampHelper.class) {
        return new CurrentTimestampHelper();
      }
      if (helperClass == FileLatchHelper.class) {
        return new FileLatchHelper();
      }
      if (helperClass == FilterRecordsHelper.class) {
        return new FilterRecordsHelper();
      }
      if (helperClass == FormatMessageHelper.class) {
        return new FormatMessageHelper();
      }
      if (helperClass == HttpCallHelper.class) {
        return new HttpCallHelper(HttpClient.newHttpClient());
      }
      if (helperClass == JsonExtractHelper.class) {
        return new JsonExtractHelper(new com.fasterxml.jackson.databind.ObjectMapper());
      }
      if (helperClass == SortRecordsHelper.class) {
        return new SortRecordsHelper();
      }
      if (helperClass == SumValuesHelper.class) {
        return new SumValuesHelper();
      }
      if (helperClass == UnreliableApiHelper.class) {
        return new UnreliableApiHelper();
      }
      throw new IllegalStateException("Cannot instantiate helper " + helperClass.getName());
    };
  }
}
