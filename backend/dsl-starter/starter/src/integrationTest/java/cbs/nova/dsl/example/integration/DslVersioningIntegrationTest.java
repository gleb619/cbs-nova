package cbs.nova.dsl.example.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import cbs.nova.config.HelperInstanceResolverConfig;
import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.dsl.GeneratedClassProvider;
import cbs.nova.starter.builder.BuilderCache;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.BuilderClientConfiguration;
import cbs.nova.starter.config.properties.DslBuilderClientProperties;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.BuilderApiErrorHandler;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.BuilderClientTestSupport;
import cbs.nova.starter.model.CompileModels.CompileRequest;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.process.DslTemporalProcessRequest;
import cbs.nova.dslexamples.v1.VersionProbeModels.VersionProbeIn;
import cbs.nova.dslexamples.v1.VersionProbeModels.VersionProbeOut;
import cbs.nova.dslexamples.v1.VersionProbeProcessWorkflow;
import cbs.nova.starter.service.TemporalDslProcessLauncher;
import cbs.nova.starter.service.TemporalTransactionInvoker;
import cbs.nova.util.ServiceUtil;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies that a running workflow keeps using the DSL version it started with after a newer DSL
 * version is loaded, while a workflow started after the reload uses the new version. A slow
 * fileLatch helper keeps the first execution open while the registry is updated; the workflow still
 * finishes with the v1 logic.
 */
@Testcontainers(disabledWithoutDocker = true)
class DslVersioningIntegrationTest {

  private static final String TASK_QUEUE = "VersionProbe-queue";
  private static final Path LATCH_DIR = Path
          .of(System.getProperty("java.io.tmpdir"), "cbs-nova-versioning-latch");
  private static final DockerImageName TEMPORAL_IMAGE = DockerImageName
          .parse("temporalio/auto-setup:1.25.2");
  private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16");

  // T601: the dsl-builder module is built from its module Dockerfile (the same one used by the
  // compose deployment) so the reload below exercises the real remote compile path. Build
  // outputs and VCS metadata are excluded from the staged context; layer caching keeps repeat
  // builds cheap once the image exists.
  private static final Path REPO_ROOT = detectRepoRoot();
  private static final Path DOCKER_CONTEXT = stageDockerContext();
  private static final DockerImageName DSL_BUILDER_IMAGE = DockerImageName
          .parse("cbs-nova/dsl-builder:t601-it");

  static {
    if (dockerAvailable()) {
      try {
        buildDslBuilderImage();
      } catch (Exception e) {
        System.err.println("[T601] dsl-builder image build failed: " + e.getMessage());
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }
  }

  @Container
  private static final GenericContainer<?> DSL_BUILDER = new GenericContainer<>(DSL_BUILDER_IMAGE)
          .withExposedPorts(8091)
          .waitingFor(Wait.forListeningPort()
                  .withStartupTimeout(Duration.ofMinutes(5)));

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
  private static io.github.resilience4j.bulkhead.ThreadPoolBulkhead builderQueue;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @BeforeAll
  static void setUp() throws IOException {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().temporalProcessLauncher().replace(null);
    cleanLatchDir();

    var globalManager = GlobalManager.globalManager();
    new DefinitionLoader().load(globalManager);
    ServiceLoader.load(GeneratedClassProvider.class,
            Thread.currentThread().getContextClassLoader())
            .stream()
            .map(ServiceLoader.Provider::get)
            .filter(provider -> provider.descriptor().name().equals("VersionProbe"))
            .forEach(globalManager::registerGeneratedClass);
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
  static void tearDown() throws Exception {
    if (workerFactory != null) {
      workerFactory.shutdown();
    }
    DslConfig.dslConfig().temporalProcessLauncher().replace(null);
    DslConfig.dslConfig().transactionInvoker().replace(null);
    DslConfig.dslConfig().helperInstanceResolver().replace(null);
    if (builderQueue != null) {
      builderQueue.close();
    }
    cleanLatchDir();
  }

  private static Path detectRepoRoot() {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null
            && !Files.exists(dir.resolve("backend/dsl-plugins/dsl-builder/Dockerfile"))) {
      dir = dir.getParent();
    }
    if (dir == null) {
      throw new IllegalStateException(
              "Cannot locate repository root (backend/dsl-plugins/dsl-builder/Dockerfile)");
    }
    return dir;
  }

  /**
   * Copies exactly the paths the dsl-builder Dockerfile consumes into a temp directory that serves
   * as the docker build context, skipping build outputs and VCS metadata.
   */
  private static Path stageDockerContext() {
    try {
      Path context = Files.createTempDirectory("dsl-builder-docker-context");
      String[] paths = {"backend/dsl-platform", "backend/dsl-plugins", "backend/dsl-starter",
          "backend/gradle", "backend/gradle.properties"};
      for (String path : paths) {
        copyFiltered(REPO_ROOT.resolve(path), context.resolve(path));
      }
      return context;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to stage dsl-builder docker context", e);
    }
  }

  private static void copyFiltered(Path source, Path target) throws IOException {
    try (Stream<Path> walk = Files.walk(source)) {
      for (Path path : walk.toList()) {
        if (isSkipped(path)) {
          continue;
        }
        Path destination = target.resolve(source.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(destination);
        } else {
          Files.createDirectories(destination.getParent());
          Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  private static boolean isSkipped(Path path) {
    for (Path part : path) {
      String name = part.toString();
      if (name.equals("build") || name.equals(".gradle") || name.equals(".git")
              || name.equals("dsl-idea-plugin")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Real {@link DslBuilderClient} aimed at the dsl-builder container. The scaffold compile inside
   * the container can cold-start a Gradle distribution and dependency downloads, so the read
   * timeout is raised well beyond the production default.
   */
  private static DslBuilderClient remoteBuilderClient() {
    var properties = new DslBuilderClientProperties(
            "http://" + DSL_BUILDER.getHost() + ":" + DSL_BUILDER.getMappedPort(8091),
            null, null, null, null, new DslBuilderClientProperties.Timeouts(5000L, 600000L));
    var errorHandler = new BuilderApiErrorHandler(new ObjectMapper());
    RestClient restClient = BuilderClientConfiguration.configureBuilder(
            RestClient.builder(), properties, errorHandler).build();
    var configuration = new BuilderClientConfiguration();
    builderQueue = configuration.dslBuilderQueue(properties);
    return new DslBuilderClient(restClient, builderQueue,
            configuration.dslBuilderBulkhead(properties),
            configuration.dslBuilderCircuitBreaker(properties),
            new BuilderCache(Caffeine.newBuilder().build()));
  }

  private static boolean dockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Exception e) {
      return false;
    }
  }

  private static void buildDslBuilderImage() throws Exception {
    Path dockerfile = REPO_ROOT.resolve("backend/dsl-plugins/dsl-builder/Dockerfile");
    ProcessBuilder pb = new ProcessBuilder(
            "docker", "build", "-t", DSL_BUILDER_IMAGE.asCanonicalNameString(),
            "-f", dockerfile.toString(), DOCKER_CONTEXT.toString());
    pb.inheritIO();
    Process process = pb.start();
    int exit = process.waitFor();
    if (exit != 0) {
      throw new IllegalStateException(
              "dsl-builder docker build failed with exit code " + exit);
    }
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
    var service = ServiceUtil.newService();

    var firstRun = service.startProcess("VersionProbe", new VersionProbeIn("first"));
    assertThat(HelperInstanceResolverConfig.LATCH_ENTERED.await(10, TimeUnit.SECONDS))
            .as("fileLatch helper should be entered")
            .isTrue();

    Path v2Dir = Path.of("src/integrationTest/resources/dsl-versioning-v2");
    var v2Props = DslProperties.builder().sourceDir(v2Dir.toString()).build();
    // T601: reload compiles remotely in the dsl-builder Testcontainer built from the module
    // Dockerfile — no in-process javac fallback exists anymore (removed in T570), so a spy on
    // the real HTTP client proves the remote compile endpoints were exercised.
    DslBuilderClient builderClient = spy(remoteBuilderClient());
    new DslReloadHandler(v2Props, new DefinitionLoader(), null, null,
            BuilderClientTestSupport.providerOf(builderClient), null, null, null)
            .reloadDefinitions();
    verify(builderClient).compile(any(CompileRequest.class));
    verify(builderClient).downloadZip(anyString());

    assertThat(GlobalManager.globalManager().findProcess("VersionProbe").orElseThrow().version())
            .as("latest registered DSL version should be v2 after reload")
            .isEqualTo("v2");

    Result<?> firstResult = firstRun.result().get(30, TimeUnit.SECONDS);
    assertThat(firstResult.isSuccess()).as("result cause: %s", firstResult.cause()).isTrue();
    VersionProbeOut firstOut = firstResult.as(VersionProbeOut.class);

    assertThat(firstOut).isNotNull();
    assertThat(firstOut.result()).isEqualTo("v1:first");

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

    // The reloaded v2 process is compiled remotely, so its declared input/output types are
    // loaded by the reload classloader and are not the v1 classpath model classes. The v2 DSL
    // therefore takes a plain String input and returns a Map; convert the Map back to the
    // classpath model for the assertion, mirroring the Temporal launcher's output conversion.
    var secondRun = service.startProcess("VersionProbe", "second");
    Result<?> secondResult = secondRun.result().get(30, TimeUnit.SECONDS);
    assertThat(secondResult.isSuccess()).as("result cause: %s", secondResult.cause()).isTrue();
    VersionProbeOut secondOut = MAPPER.convertValue(secondResult.value(), VersionProbeOut.class);

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
    public Map<String, Object> dslSignalState() {
      return Map.of();
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
    return new HelperInstanceResolverConfig().helperInstanceResolver();
  }
}
