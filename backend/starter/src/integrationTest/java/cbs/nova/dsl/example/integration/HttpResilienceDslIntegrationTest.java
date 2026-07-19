package cbs.nova.dsl.example.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.HelperInstanceResolver;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.dslexamples.HttpResilienceModels.HttpResilienceProcessIn;
import cbs.nova.dslexamples.HttpResilienceModels.HttpResilienceProcessOut;
import cbs.nova.starter.helpers.CompensationTrackerHelper;
import cbs.nova.starter.helpers.model.HttpCallIn;
import cbs.nova.starter.services.TemporalDslProcessLauncher;
import cbs.nova.starter.services.TemporalDslProcessService;
import cbs.nova.starter.services.TemporalTransactionInvoker;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * End-to-end Temporal resilience test against a real HTTP transport (WireMock). Exercises
 * RetryPolicy retries, failure propagation, compensation, and timeout using the built-in
 * {@code httpCall} helper.
 */
@Testcontainers
class HttpResilienceDslIntegrationTest {

  private static final String TASK_QUEUE = "http-resilience-queue";
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

  private WireMockServer wireMock;

  @BeforeAll
  static void setUp() {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().temporalProcessLauncher().replace(null);

    var globalManager = GlobalManager.globalManager();
    new DefinitionLoader().load(globalManager);
    DslConfig.dslConfig().helperInstanceResolver().replace(reflectiveHelperResolver());
    globalManager.registerHelperResolvers();

    assertThat(globalManager.hasProcess("HttpResilienceSuccess")).isTrue();
    assertThat(globalManager.hasProcess("HttpResilienceCompensated")).isTrue();
    assertThat(globalManager.hasProcess("HttpResilienceUncaught")).isTrue();
    assertThat(globalManager.hasTransaction("httpCallTxResilient")).isTrue();
    assertThat(globalManager.hasTransaction("httpCallTxFragile")).isTrue();

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
    registerProcess(worker, "HttpResilienceSuccess");
    registerProcess(worker, "HttpResilienceCompensated");
    registerProcess(worker, "HttpResilienceUncaught");
    registerTransaction(worker, "httpCallTxResilient");
    registerTransaction(worker, "httpCallTxFragile");
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

  @BeforeEach
  void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();
  }

  @AfterEach
  void stopWireMock() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  private String baseUrl() {
    return "http://localhost:" + wireMock.port();
  }

  @Test
  void resilientTransactionSucceedsAfterTemporalRetries() {
    wireMock.stubFor(get(urlEqualTo("/probe"))
            .inScenario("transient-500")
            .whenScenarioStateIs("Started")
            .willSetStateTo("failed-once")
            .willReturn(aResponse().withStatus(500).withBody("boom 1")));
    wireMock.stubFor(get(urlEqualTo("/probe"))
            .inScenario("transient-500")
            .whenScenarioStateIs("failed-once")
            .willSetStateTo("failed-twice")
            .willReturn(aResponse().withStatus(500).withBody("boom 2")));
    wireMock.stubFor(get(urlEqualTo("/probe"))
            .inScenario("transient-500")
            .whenScenarioStateIs("failed-twice")
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    var service = new TemporalDslProcessService(
            new ContextFactory(), new InMemoryDslRunRepository(), new ObjectMapper());
    String runId = "http-resilience-success-" + System.currentTimeMillis();
    var input = new HttpResilienceProcessIn(runId,
            HttpCallIn.get(baseUrl() + "/probe"));

    Result<?> result = service.runProcess("HttpResilienceSuccess", input);

    assertThat(result.isSuccess())
            .as("success result cause: %s", result.cause())
            .isTrue();
    HttpResilienceProcessOut out = result.as(HttpResilienceProcessOut.class);
    assertThat(out).isNotNull();
    assertThat(out.status()).isEqualTo("SUCCESS");
  }

  @Test
  void fragileTransactionFailsAndProcessCompensates() {
    var tracker = tracker();
    wireMock.stubFor(get(urlEqualTo("/probe"))
            .willReturn(aResponse().withStatus(500).withBody("persistent failure")));

    String runId = "http-resilience-compensated-" + System.currentTimeMillis();
    var input = new HttpResilienceProcessIn(runId,
            HttpCallIn.get(baseUrl() + "/probe"));
    String markerId = "HttpResilienceCompensated-" + input.scenario();

    Result<?> result = new TemporalDslProcessService(
            new ContextFactory(), new InMemoryDslRunRepository(), new ObjectMapper())
            .runProcess("HttpResilienceCompensated", input);

    assertThat(result.isSuccess()).isFalse();
    assertThat(tracker.wasCompensated(markerId)).isTrue();
  }

  @Test
  void fragileTransactionFailsWithoutCompensationWhenNoneConfigured() {
    var tracker = tracker();
    wireMock.stubFor(get(urlEqualTo("/probe"))
            .willReturn(aResponse().withStatus(500).withBody("persistent failure")));

    String runId = "http-resilience-uncaught-" + System.currentTimeMillis();
    String markerId = "HttpResilienceCompensated-" + runId;
    var input = new HttpResilienceProcessIn(runId,
            HttpCallIn.get(baseUrl() + "/probe"));

    Result<?> result = new TemporalDslProcessService(
            new ContextFactory(), new InMemoryDslRunRepository(), new ObjectMapper())
            .runProcess("HttpResilienceUncaught", input);

    assertThat(result.isSuccess()).isFalse();
    assertThat(tracker.wasCompensated(markerId)).isFalse();
  }

  @Test
  void delayedStubExceedingTimeoutFailsWithoutHanging() {
    wireMock.stubFor(get(urlEqualTo("/slow"))
            .willReturn(aResponse()
                    .withFixedDelay(2_000)
                    .withStatus(200)
                    .withBody("too late")));

    String runId = "http-resilience-timeout-" + System.currentTimeMillis();
    var input = new HttpResilienceProcessIn(runId,
            new HttpCallIn(baseUrl() + "/slow", "GET", null, null, 200L, null));

    Result<?> result = new TemporalDslProcessService(
            new ContextFactory(), new InMemoryDslRunRepository(), new ObjectMapper())
            .runProcess("HttpResilienceUncaught", input);

    assertThat(result.isSuccess()).isFalse();
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
        return (Executable<?, ?>) helperClass.getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot instantiate helper " + helperClass, e);
      }
    };
  }
}
