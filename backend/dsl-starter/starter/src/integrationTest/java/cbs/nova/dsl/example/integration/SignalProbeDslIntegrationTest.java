package cbs.nova.dsl.example.integration;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.dsl.GeneratedClassProvider;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.process.DslTemporalProcess;
import cbs.nova.dsl.process.DslTemporalProcessRequest;
import cbs.nova.starter.service.DslSignalService;
import cbs.nova.starter.service.TemporalDslProcessLauncher;
import cbs.nova.starter.service.TemporalTransactionInvoker;
import cbs.nova.util.ServiceUtil;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
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
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Testcontainers
class SignalProbeDslIntegrationTest {

  private static final String TASK_QUEUE = "SignalProbe-queue";
  private static final DockerImageName TEMPORAL_IMAGE = DockerImageName
          .parse("temporalio/auto-setup:1.25.2");
  private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16");

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
    ServiceLoader.load(GeneratedClassProvider.class,
            Thread.currentThread().getContextClassLoader())
            .stream()
            .map(ServiceLoader.Provider::get)
            .filter(provider -> provider.descriptor().name().equals("SignalProbe"))
            .forEach(globalManager::registerGeneratedClass);
    assertThat(globalManager.hasProcess("SignalProbe")).isTrue();
    assertThat(globalManager.hasGeneratedProcess("SignalProbe")).isTrue();

    var serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                    .setTarget(
                            TEMPORAL.getHost() + ":" + TEMPORAL.getMappedPort(7233))
                    .build());
    workflowClient = WorkflowClient.newInstance(serviceStubs);

    var launcher = new TemporalDslProcessLauncher(workflowClient,
            JsonMapper.builder().build(), Duration.ofSeconds(30), Duration.ofSeconds(5));
    DslConfig.dslConfig().temporalProcessLauncher().replace(launcher);
    DslConfig.dslConfig().transactionInvoker().replace(new TemporalTransactionInvoker());

    var descriptor = globalManager.findGeneratedProcess("SignalProbe").orElseThrow();
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
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void workflowBranchesOnSignal() throws Exception {
    String runId = "signal-probe-" + UUID.randomUUID();

    @SuppressWarnings("unchecked")
    Class<DslTemporalProcess<String>> iface = (Class<DslTemporalProcess<String>>) GlobalManager
            .globalManager()
            .findGeneratedProcess("SignalProbe").orElseThrow().temporalInterface();

    var stub = workflowClient.newWorkflowStub(iface,
            WorkflowOptions.newBuilder()
                    .setWorkflowId(runId)
                    .setTaskQueue(TASK_QUEUE)
                    .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                    .setWorkflowTaskTimeout(Duration.ofSeconds(5))
                    .build());

    CompletableFuture<Object> result = CompletableFuture.supplyAsync(() -> {
      try {
        return ((DslTemporalProcess<String>) stub).execute(
                new DslTemporalProcessRequest<>(runId, "request-body"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // Give workflow a moment to start and reach the await.
    Thread.sleep(500);

    Map<String, Object> payload = Map.of("approved", true, "reviewer", "alice");
    WorkflowStub untyped = workflowClient.newUntypedWorkflowStub(runId);
    untyped.signal("approval", payload);

    assertThat(result.get()).isEqualTo("approved=true:alice:request-body");
  }

  @Test
  void dslSignalServiceSendsSignal() throws Exception {
    String runId = "signal-svc-" + UUID.randomUUID();

    @SuppressWarnings("unchecked")
    Class<DslTemporalProcess<String>> iface = (Class<DslTemporalProcess<String>>) GlobalManager
            .globalManager()
            .findGeneratedProcess("SignalProbe").orElseThrow().temporalInterface();

    var stub = workflowClient.newWorkflowStub(iface,
            WorkflowOptions.newBuilder()
                    .setWorkflowId(runId)
                    .setTaskQueue(TASK_QUEUE)
                    .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                    .setWorkflowTaskTimeout(Duration.ofSeconds(5))
                    .build());

    CompletableFuture<Object> result = CompletableFuture.supplyAsync(() -> {
      try {
        return ((DslTemporalProcess<String>) stub).execute(
                new DslTemporalProcessRequest<>(runId, "svc-body"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Thread.sleep(500);

    DslSignalService signalService = new DslSignalService(workflowClient,
            new cbs.nova.dsl.history.DslRunRepository() {
              @Override
              public cbs.nova.dsl.history.DslRun save(cbs.nova.dsl.history.DslRun run) {
                return run;
              }
              @Override
              public java.util.Optional<cbs.nova.dsl.history.DslRun> findByRunId(String id) {
                return java.util.Optional.of(cbs.nova.dsl.history.DslRun.builder()
                        .runId(id).processName("SignalProbe").status("RUNNING")
                        .startedAt(java.time.Instant.now()).build());
              }
              @Override
              public java.util.List<cbs.nova.dsl.history.DslRun> findByProcessName(String name) {
                return java.util.List.of();
              }
              @Override
              public cbs.nova.dsl.history.DslRunSearchResult search(String a, String b, String c,
                      String d, int e, int f) {
                return null;
              }
              @Override
              public cbs.nova.dsl.history.DslRun updateFinished(String a, String b, String o,
                      String e, java.time.Instant f, String g) {
                return null;
              }
              @Override
              public int updateFinishedIfRunning(String a, String b, String o, String e,
                      java.time.Instant f, String g) {
                return 0;
              }
            });

    Map<String, Object> payload = Map.of("approved", true, "reviewer", "bob");
    signalService.sendSignal(runId, "approval", payload);

    assertThat(result.get()).isEqualTo("approved=true:bob:svc-body");
  }
}
