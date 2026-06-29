package cbs.nova.showcase;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.dsl.api.ConditionDefinition;
import cbs.dsl.api.DslDefinition;
import cbs.dsl.api.DslComponentResolver;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.HelperDefinition;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.evaluator.Evaluator;
import cbs.dsl.evaluator.RegistryEventEvaluator;
import cbs.dsl.evaluator.RegistryHelperEvaluator;
import cbs.dsl.evaluator.RegistryTransactionEvaluator;
import cbs.nova.registry.DslRegistry;
import cbs.nova.registry.SpiImplRegistryLoader;
import cbs.nova.sample.SampleHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.util.ArrayList;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.util.function.BiFunction;

/**
 * Abstract base for showcase tests that compile DSL files in a Gradle Testcontainer and register
 * the resulting objects in a {@link DslRegistry}.
 */
@Testcontainers
abstract class ShowcaseTestBase {

  @Container
  static GenericContainer<?> gradleContainer = new GenericContainer<>(
          DockerImageName.parse("gradle:jdk25"))
      .withCommand("tail", "-f", "/dev/null");

  @TempDir
  static Path sharedTempDir;

  protected DslRegistry dslRegistry;
  protected DslComponentResolver resolver;

  @BeforeEach
  void setUpBase() throws Exception {
    dslRegistry = new DslRegistry();
    resolver = new TestDslComponentResolver();
    SpiImplRegistryLoader.loadInto(dslRegistry, resolver);
    compileDslAndRegister();
  }

  protected void compileDslAndRegister() throws Exception {
    Path tempDir = Files.createTempDirectory(sharedTempDir, "dsl-sample1");
    try {
      prepareDslProject(tempDir);
      runGradleCompilation(tempDir);
      loadAndRegisterGeneratedDefinitions(tempDir);
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
    copyResource("dsl/sample1/SampleHelperDsl.java", tempDir.resolve("SampleHelperDsl.java"));

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
        gradleContainer.execInContainer("find", "/project/build/dsl-classes", "-type", "f");
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
    compileGeneratedJavaSources(outputDir);
  }

  protected void loadAndRegisterGeneratedDefinitions(Path tempDir) throws Exception {
    Path outputDir = tempDir.resolve("build-output");
    Path classDir = Files.exists(outputDir.resolve("main")) ? outputDir.resolve("main") : outputDir;
    Path definitionsDir = classDir.resolve("cbs/dsl/codegen/generated/definitions");
    if (!Files.exists(definitionsDir)) {
      throw new IllegalStateException("Generated definitions not found in " + definitionsDir);
    }

    URLClassLoader classLoader =
        new URLClassLoader(new URL[] {classDir.toUri().toURL()}, getClass().getClassLoader());

    List<Class<?>> classes = new ArrayList<>();
    try (var walk = Files.walk(definitionsDir)) {
      walk
          .filter(p -> p.toString().endsWith(".class"))
          .map(p -> classDir.relativize(p).toString().replace('/', '.').replace("\\", ".").replaceAll("\\.class$", ""))
          .map(p -> loadClass(classLoader, p))
          .filter(c -> DslDefinition.class.isAssignableFrom(c))
          .forEach(classes::add);

      for (Class<?> clazz : classes) {
        Object instance = clazz.getDeclaredConstructor(DslComponentResolver.class).newInstance(resolver);
        switch (instance) {
          case HelperDefinition h -> dslRegistry.register(h);
          case EventDefinition e -> dslRegistry.register(e);
          case TransactionDefinition t -> dslRegistry.register(t);
          case WorkflowDefinition w -> dslRegistry.register(w);
          case ConditionDefinition c -> dslRegistry.register(c);
          case MassOperationDefinition m -> dslRegistry.register(m);
          default -> throw new IllegalStateException("Unsupported definition: " + instance.getClass());
        }
      }
    }
  }

  private Class<?> loadClass(URLClassLoader classLoader, String name) {
    try {
      return Class.forName(name, false, classLoader);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Cannot load generated class: " + name, e);
    }
  }

  protected void compileGeneratedJavaSources(Path outputDir) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("JDK compiler not available");
    }

    List<File> sourceFiles;
    try (var walk = Files.walk(outputDir)) {
      sourceFiles = walk
          .filter(p -> p.toString().endsWith(".java"))
          .map(Path::toFile)
          .toList();
    }
    if (sourceFiles.isEmpty()) {
      return;
    }

    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
      fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDir.toFile()));
      Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(sourceFiles);

      List<String> options = new ArrayList<>();
      options.add("-implicit:class");
      String baseCp = System.getProperty("java.class.path");
      String cp = baseCp == null || baseCp.isEmpty()
          ? outputDir.toAbsolutePath().toString()
          : outputDir.toAbsolutePath() + File.pathSeparator + baseCp;
      options.add("-classpath");
      options.add(cp);

      List<String> diagnosticMessages = new ArrayList<>();
      JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, d -> diagnosticMessages.add(d.toString()), options, null, units);
      if (!task.call() || !diagnosticMessages.isEmpty()) {
        throw new IllegalStateException("Generated DSL source compilation failed: " + String.join("\n", diagnosticMessages));
      }
    }
  }

  protected void deleteRecursively(Path path) throws Exception {
    if (Files.isDirectory(path)) {
      try (var entries = Files.list(path)) {
        for (Path entry : (Iterable<Path>) entries::iterator) {
          deleteRecursively(entry);
        }
      }
    }
    Files.deleteIfExists(path);
  }

  protected void runTestInGradleContainer(String taskName, String... extraArgs) throws Exception {
    String[] args = new String[extraArgs.length + 2];
    args[0] = "bash";
    args[1] = "-c";
    args[2] = "cd /project && gradle " + taskName + " " + String.join(" ", extraArgs);
    ExecResult result = gradleContainer.execInContainer(args);
    assertThat(result.getExitCode())
        .withFailMessage(
            "Gradle %s failed. Stdout: %s%nStderr: %s",
            taskName, result.getStdout(), result.getStderr())
        .isZero();
  }

  protected Map<String, Object> wrapHelperInput(HelperInput input) {
    return input.params();
  }

  protected BiFunction<Map<String, Object>, Map<String, Object>, Boolean> helperAssertion() {
    return (expected, actual) -> expected.equals(actual);
  }

  public class TestDslComponentResolver implements DslComponentResolver {

    @Override
    public <T> T resolve(Class<T> type) {
      if (type == Evaluator.class) {
        return type.cast(new Evaluator(
            new RegistryEventEvaluator(dslRegistry),
            new RegistryHelperEvaluator(dslRegistry),
            new RegistryTransactionEvaluator(dslRegistry)));
      }
      if (type == SampleHelper.class) {
        return type.cast(new SampleHelper());
      }
      return Mockito.mock(type);
    }
  }
}
