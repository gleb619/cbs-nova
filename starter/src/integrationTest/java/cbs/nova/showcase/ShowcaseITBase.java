package cbs.nova.showcase;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.dsl.api.DslDefinition;
import cbs.dsl.api.DslDefinitionCollector;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.nova.registry.DslRegistry;
import cbs.nova.registry.SpiImplRegistryLoader;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Abstract base for showcase tests that compile DSL files in a Gradle Testcontainer
 * and register the resulting definitions in a {@link DslRegistry}.
 */
@Testcontainers
abstract class ShowcaseITBase {

  @Container
  static GenericContainer<?> gradleContainer = new GenericContainer<>(
          DockerImageName.parse("gradle:jdk25"))
      .withCommand("tail", "-f", "/dev/null");

  @TempDir
  static Path sharedTempDir;

  protected DslRegistry dslRegistry;

  @BeforeEach
  void setUpBase() throws Exception {
    DslDefinitionCollector.clear();
    dslRegistry = new DslRegistry();
    SpiImplRegistryLoader.loadInto(dslRegistry);
    compileDslAndRegister();
  }

  protected void compileDslAndRegister() throws Exception {
    Path tempDir = Files.createTempDirectory(sharedTempDir, "dsl-sample1");
    try {
      prepareDslProject(tempDir);
      runGradleCompilation(tempDir);
      loadAndRegisterDefinitions(
          tempDir, new String[] {"SampleEventDsl", "SampleTransactionDsl", "SampleWorkflowDsl"});
    } finally {
      deleteRecursively(tempDir);
    }
  }

  protected void prepareDslProject(Path tempDir) throws Exception {
    Path libsDir = tempDir.resolve("libs");
    Files.createDirectories(libsDir);

    copyResource("dsl/sample1/build.gradle", tempDir.resolve("build.gradle"));
    copyResource("dsl/sample1/SampleEventDsl.java", tempDir.resolve("SampleEventDsl.java"));
    copyResource(
        "dsl/sample1/SampleTransactionDsl.java", tempDir.resolve("SampleTransactionDsl.java"));
    copyResource("dsl/sample1/SampleWorkflowDsl.java", tempDir.resolve("SampleWorkflowDsl.java"));

    String dslApiJar = findDslApiJar();
    String dslCodegenJar = findDslCodegenJar();
    Files.copy(Path.of(dslApiJar), libsDir.resolve("dsl-api.jar"));
    Files.copy(Path.of(dslCodegenJar), libsDir.resolve("dsl-codegen.jar"));

    gradleContainer.copyFileToContainer(
        MountableFile.forHostPath(tempDir.toAbsolutePath()), "/project");
  }

  protected String findDslApiJar() throws Exception {
    Path buildDir = Path.of("..", "dsl-api", "build", "libs").toAbsolutePath().normalize();
    return Files.list(buildDir)
        .filter(p -> p.getFileName().toString().endsWith(".jar")
            && !p.getFileName().toString().contains("-sources"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("dsl-api JAR not found"))
        .toAbsolutePath()
        .toString();
  }

  protected String findDslCodegenJar() throws Exception {
    Path buildDir =
        Path.of("..", "dsl-codegen", "build", "libs").toAbsolutePath().normalize();
    return Files.list(buildDir)
        .filter(p -> p.getFileName().toString().endsWith(".jar")
            && !p.getFileName().toString().contains("-sources"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("dsl-codegen JAR not found"))
        .toAbsolutePath()
        .toString();
  }

  protected void copyResource(String resourcePath, Path dest) throws Exception {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IllegalStateException("Resource not found: " + resourcePath);
      }
      Files.copy(in, dest);
    }
  }

  protected void runGradleCompilation(Path tempDir) throws Exception {
    ExecResult result =
        gradleContainer.execInContainer("bash", "-c", "cd /project && gradle compileDsl");
    assertThat(result.getExitCode())
        .withFailMessage(
            "Gradle compilation failed. Stdout: %s%nStderr: %s",
            result.getStdout(), result.getStderr())
        .isZero();

    Path outputDir = tempDir.resolve("build-output");
    Files.createDirectories(outputDir);

    ExecResult lsResult =
        gradleContainer.execInContainer("find", "/project/build/dsl-classes", "-name", "*.class");
    String[] files = lsResult.getStdout().split("\n");
    for (String file : files) {
      if (file.trim().isEmpty()) {
        continue;
      }
      String relative = file.replace("/project/build/dsl-classes/", "");
      Path dest = outputDir.resolve(relative);
      Files.createDirectories(dest.getParent());
      gradleContainer.copyFileFromContainer(file, dest.toString());
    }
  }

  protected void loadAndRegisterDefinitions(Path tempDir, String[] dslFiles) throws Exception {
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
  }

  protected void deleteRecursively(Path path) {
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

  protected BiFunction<String, Map<String, Object>, Object> helperResolver() {
    return (name, params) -> dslRegistry
        .resolveHelper(name)
        .execute(new HelperInput(params, "SAMPLE_EVENT_DSL", null))
        .value();
  }
}
