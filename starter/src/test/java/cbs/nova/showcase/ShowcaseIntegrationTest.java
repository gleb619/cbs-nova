package cbs.nova.showcase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.dsl.api.Action;
import cbs.dsl.api.DslDefinition;
import cbs.dsl.api.DslDefinitionCollector;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.dsl.builder.WorkflowDsl;
import cbs.nova.model.EventExecutionRequest;
import cbs.nova.model.EventExecutionResponse;
import cbs.nova.model.WorkflowExecutionResponse;
import cbs.nova.registry.DslRegistry;
import cbs.nova.registry.SpiImplRegistryLoader;
import cbs.nova.service.ContextEncryptionService;
import cbs.nova.service.EventExecutionService;
import cbs.nova.service.WorkflowExecutor;
import cbs.nova.service.WorkflowResolver;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Integration test that verifies the full DSL → compile → registry → execution chain.
 *
 * <ol>
 *   <li>Compiles Java 25 implicit-class DSL files in a Gradle Testcontainer.
 *   <li>Loads the compiled classes, invokes {@code main()}, and drains the
 *       {@link DslDefinitionCollector}.
 *   <li>Registers them in the {@link DslRegistry} alongside SPI-discovered components.
 *   <li>Executes the event context block (with helper resolution) directly.
 *   <li>Executes the DSL transaction directly.
 *   <li>Runs end-to-end through {@link EventExecutionService} with a mocked Temporal client.
 * </ol>
 */
@Testcontainers
class ShowcaseIntegrationTest {

  @Container
  static GenericContainer<?> gradleContainer = new GenericContainer<>(
          DockerImageName.parse("gradle:jdk25"))
      .withCommand("tail", "-f", "/dev/null");

  @TempDir
  static Path sharedTempDir;

  private DslRegistry dslRegistry;
  private EventExecutionService eventExecutionService;

  @BeforeEach
  void setUp() throws Exception {
    DslDefinitionCollector.clear();
    dslRegistry = new DslRegistry();
    SpiImplRegistryLoader.loadInto(dslRegistry);

    compileDslAndRegister();
    buildEventExecutionService();
  }

  private void buildEventExecutionService() {
    WorkflowResolver workflowResolver = new WorkflowResolver(dslRegistry);

    WorkflowClient workflowClient = mock(WorkflowClient.class);
    WorkflowStub workflowStub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub(anyString(), any(WorkflowOptions.class)))
        .thenReturn(workflowStub);
    when(workflowStub.start(any())).thenReturn(WorkflowExecution.newBuilder().build());
    when(workflowStub.getResult(WorkflowExecutionResponse.class))
        .thenReturn(new WorkflowExecutionResponse(42L, "DONE"));

    WorkflowExecutor workflowExecutor = new WorkflowExecutor(workflowClient);
    ReflectionTestUtils.setField(workflowExecutor, "taskQueue", "TEST_TASK_QUEUE");

    ContextEncryptionService encryptionService = new ContextEncryptionService(new ObjectMapper());

    eventExecutionService = new EventExecutionService(
        workflowResolver, workflowExecutor, encryptionService, dslRegistry);
  }

  private void compileDslAndRegister() throws Exception {
    Path tempDir = Files.createTempDirectory(sharedTempDir, "dsl-sample1");
    try {
      prepareDslProject(tempDir);
      runGradleCompilation(tempDir);
      loadAndRegisterDefinitions(tempDir, new String[]{"SampleEventDsl", "SampleTransactionDsl"});
    } finally {
      deleteRecursively(tempDir);
    }
  }

  private void prepareDslProject(Path tempDir) throws Exception {
    Path libsDir = tempDir.resolve("libs");
    Files.createDirectories(libsDir);

    copyResource("dsl/sample1/build.gradle", tempDir.resolve("build.gradle"));
    copyResource("dsl/sample1/SampleEventDsl.java", tempDir.resolve("SampleEventDsl.java"));
    copyResource(
        "dsl/sample1/SampleTransactionDsl.java", tempDir.resolve("SampleTransactionDsl.java"));

    Path dslApiJar = resolveDslApiJar();
    Files.copy(dslApiJar, libsDir.resolve("dsl-api.jar"));

    Path dslCodegenJar = resolveDslCodegenJar();
    Files.copy(dslCodegenJar, libsDir.resolve("dsl-codegen.jar"));
  }

  private void copyResource(String resourcePath, Path target) throws Exception {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(is).isNotNull();
      Files.copy(is, target);
    }
  }

  private Path resolveDslApiJar() throws Exception {
    Path projectRoot = findProjectRoot();
    Path jar = projectRoot.resolve("dsl-api/build/libs/dsl-api-0.0.1-SNAPSHOT.jar");
    if (!Files.exists(jar)) {
      ProcessBuilder pb = new ProcessBuilder("./gradlew", ":dsl-api:jar");
      pb.directory(projectRoot.toFile());
      pb.inheritIO();
      int exitCode = pb.start().waitFor();
      assertThat(exitCode).withFailMessage("Failed to build dsl-api JAR").isZero();
    }
    assertThat(jar).exists();
    return jar;
  }

  private Path resolveDslCodegenJar() throws Exception {
    Path projectRoot = findProjectRoot();
    Path jar = projectRoot.resolve("dsl-codegen/build/libs/dsl-codegen-0.0.1-SNAPSHOT.jar");
    if (!Files.exists(jar)) {
      ProcessBuilder pb = new ProcessBuilder("./gradlew", ":dsl-codegen:jar");
      pb.directory(projectRoot.toFile());
      pb.inheritIO();
      int exitCode = pb.start().waitFor();
      assertThat(exitCode).withFailMessage("Failed to build dsl-codegen JAR").isZero();
    }
    assertThat(jar).exists();
    return jar;
  }

  private Path findProjectRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (current != null) {
      if (Files.exists(current.resolve("gradlew"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Could not find project root (no gradlew found in ancestors of "
        + System.getProperty("user.dir")
        + ")");
  }

  private void runGradleCompilation(Path tempDir) throws Exception {
    gradleContainer.execInContainer("mkdir", "-p", "/project/libs");

    gradleContainer.copyFileToContainer(
        MountableFile.forHostPath(tempDir.resolve("build.gradle").toString()),
        "/project/build.gradle");
    gradleContainer.copyFileToContainer(
        MountableFile.forHostPath(tempDir.resolve("SampleEventDsl.java").toString()),
        "/project/SampleEventDsl.java");
    gradleContainer.copyFileToContainer(
        MountableFile.forHostPath(tempDir.resolve("SampleTransactionDsl.java").toString()),
        "/project/SampleTransactionDsl.java");
    gradleContainer.copyFileToContainer(
        MountableFile.forHostPath(tempDir.resolve("libs/dsl-api.jar").toString()),
        "/project/libs/dsl-api.jar");
    gradleContainer.copyFileToContainer(
        MountableFile.forHostPath(tempDir.resolve("libs/dsl-codegen.jar").toString()),
        "/project/libs/dsl-codegen.jar");

    ExecResult result =
        gradleContainer.execInContainer("sh", "-c", "cd /project && gradle compileDsl");

    assertThat(result.getExitCode())
        .withFailMessage(
            "Gradle compilation failed:\nSTDOUT:\n%s\nSTDERR:\n%s",
            result.getStdout(), result.getStderr())
        .isZero();

    Path outputDir = tempDir.resolve("build-output");
    Files.createDirectories(outputDir);

    ExecResult lsResult =
        gradleContainer.execInContainer("find", "/project/build/dsl-classes", "-name", "*.class");
    String[] files = lsResult.getStdout().split("\n");
    for (String file : files) {
      if (file.trim().isEmpty()) continue;
      String relative = file.replace("/project/build/dsl-classes/", "");
      Path dest = outputDir.resolve(relative);
      Files.createDirectories(dest.getParent());
      gradleContainer.copyFileFromContainer(file, dest.toString());
    }
  }

  private void loadAndRegisterDefinitions(Path tempDir, String[] dslFiles) throws Exception {
    Path outputDir = tempDir.resolve("build-output");
    Path classDir = Files.exists(outputDir.resolve("main")) ? outputDir.resolve("main") : outputDir;

    URLClassLoader classLoader =
        new URLClassLoader(new URL[] {classDir.toUri().toURL()}, getClass().getClassLoader());

    for (String className : dslFiles) {
      DslDefinitionCollector.clear();
      Class<?> clazz = classLoader.loadClass(className);
      Method mainMethod = clazz.getDeclaredMethod("main", String[].class);
      mainMethod.invoke(null, (Object) new String[0]);

      for (DslDefinition def : DslDefinitionCollector.drain()) {
        if (def instanceof EventDefinition event
            && !dslRegistry.getEvents().containsKey(event.getCode())) {
          dslRegistry.register(event);
        } else if (def instanceof TransactionDefinition tx
            && !dslRegistry.getTransactions().containsKey(tx.getCode())) {
          dslRegistry.register(tx);
        } else if (def instanceof WorkflowDefinition wf
            && !dslRegistry.getWorkflows().containsKey(wf.getCode())) {
          dslRegistry.register(wf);
        }
      }
    }

    if (!dslRegistry.getWorkflows().containsKey("DSL_TEST_WF")) {
      EventDefinition registeredEvent = dslRegistry.resolveEvent("SAMPLE_EVENT_DSL");
      //TODO: move to `starter/src/test/resources/dsl/sample1/SampleWorkflowDsl.java` instead and use codegeneration
      WorkflowDefinition workflow = WorkflowDsl.workflow("DSL_TEST_WF")
          .states("START", "DONE")
          .initial("START")
          .terminal("DONE")
          .transition("START", "DONE", Action.SUBMIT, registeredEvent)
          .build();
      dslRegistry.register(workflow);
    }
  }

  private void deleteRecursively(Path path) {
    try {
      if (Files.isDirectory(path)) {
        try (var entries = Files.list(path)) {
          entries.forEach(this::deleteRecursively);
        }
      }
      Files.deleteIfExists(path);
    } catch (Exception e) {
      // Best-effort cleanup of temp directory
    }
  }

  @Test
  @DisplayName("Should compile DSL files in Gradle container and register definitions")
  void shouldCompileDslInGradleContainerAndRegisterDefinitions() {
    assertThat(dslRegistry.getEvents()).containsKey("SAMPLE_EVENT_DSL");
    assertThat(dslRegistry.getTransactions()).containsKey("SAMPLE_TRANSACTION_DSL");
    assertThat(dslRegistry.getWorkflows()).containsKey("DSL_TEST_WF");
  }

  @Test
  @DisplayName("Should execute DSL event context block with helper resolution")
  void shouldExecuteDslEventContextBlockWithHelperResolution() {
    EventDefinition eventDef = dslRegistry.resolveEvent("SAMPLE_EVENT_DSL");

    EnrichmentContext ctx =
        new EnrichmentContext("SAMPLE_EVENT_DSL", 0L, "testUser", "dev", Map.of("name", "PoC"));
    ctx.setHelperResolver(helperResolver());

    eventDef.getContextBlock().accept(ctx);

    assertThat(ctx.getEnrichment()).containsKey("enriched");
    assertThat(ctx.getEnrichment().get("enriched")).isEqualTo(Map.of("result", "PoC!"));
  }

  @Test
  @DisplayName("Should execute DSL transaction directly")
  void shouldExecuteDslTransactionDirectly() {
    TransactionDefinition txDef = dslRegistry.resolveTransaction("SAMPLE_TRANSACTION_DSL");

    TransactionInput input =
        new TransactionInput(Map.of("name", "PoC"), "SAMPLE_TRANSACTION_DSL", null, "dev");
    var output = txDef.execute(input);

    assertThat(output.result()).containsEntry("greeting", "DSL TX says hello to PoC");
  }

  @Test
  @DisplayName("Should execute DSL event through EventExecutionService")
  void shouldExecuteDslEventThroughEventExecutionService() {
    EventExecutionRequest request = new EventExecutionRequest(
        "DSL_TEST_WF", "SAMPLE_EVENT_DSL", "testUser", Map.of("name", "PoC"));

    EventExecutionResponse response = eventExecutionService.execute(request);

    assertThat(response).isNotNull();
    assertThat(response.executionId()).isEqualTo(42L);
    assertThat(response.status()).isEqualTo("DONE");
  }

  private BiFunction<String, Map<String, Object>, Object> helperResolver() {
    return (name, params) -> dslRegistry
        .resolveHelper(name)
        .execute(new HelperInput(params, "SAMPLE_EVENT_DSL", null))
        .value();
  }
}
