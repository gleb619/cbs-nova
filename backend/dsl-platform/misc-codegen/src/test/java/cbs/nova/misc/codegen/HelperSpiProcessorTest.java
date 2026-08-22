package cbs.nova.misc.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.helper.HelperRegistrar;
import cbs.nova.dsl.helper.HelperResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;

@Slf4j
class HelperSpiProcessorTest {

  @TempDir
  Path tempDir;

  @Test
  void processorGeneratesResolverForValidHelper() throws Exception {
    var srcDir = tempDir.resolve("src/fixture");
    Files.createDirectories(srcDir);
    var fixtureSource = srcDir.resolve("TestGreetHelper.java");
    Files.writeString(fixtureSource, """
            package fixture;

            import cbs.nova.dsl.Context;
            import cbs.nova.dsl.Executable;
            import cbs.nova.dsl.annotation.Helper;
            import cbs.nova.dsl.Result;

            @Helper(name = "greetHelper")
            public class TestGreetHelper implements Executable<String, String> {
              @Override
              public Result<String> execute(Context<String> ctx) {
                return Result.success("Hello, " + ctx.body());
              }
            }
            """);

    var outputDir = tempDir.resolve("out");
    Files.createDirectories(outputDir);

    String classpath = System.getProperty("java.class.path");

    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var fileManager = compiler.getStandardFileManager(diagnostics, null, null);

    var compilationUnits = fileManager.getJavaFileObjects(fixtureSource.toFile());

    var options = new ArrayList<>(List.of(
            "-cp", classpath,
            "-processorpath", classpath,
            "-processor", HelperSpiProcessor.class.getName(),
            "-d", outputDir.toString(),
            "-source", "25",
            "-target", "25"));

    var task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
    boolean success = task.call();

    diagnostics.getDiagnostics()
            .forEach(d -> log.info("{}: {}", d.getKind(), d.getMessage(null)));

    assertThat(success).isTrue();

    var resolverClass = outputDir.resolve("fixture/GeneratedHelperResolver.class");
    assertThat(resolverClass).exists();

    var spiFile = outputDir.resolve("META-INF/services/cbs.nova.dsl.helper.HelperResolver");
    assertThat(spiFile).exists();
    assertThat(Files.readString(spiFile).strip())
            .isEqualTo("fixture.GeneratedHelperResolver");

    var instanceResolverClass = outputDir.resolve("fixture/GeneratedHelperInstanceResolver.class");
    assertThat(instanceResolverClass).exists();

    var instanceResolverSpi = outputDir
            .resolve("META-INF/services/cbs.nova.dsl.helper.HelperInstanceResolver");
    assertThat(instanceResolverSpi).exists();
    assertThat(Files.readString(instanceResolverSpi).strip())
            .isEqualTo("fixture.GeneratedHelperInstanceResolver");

    try (var loader = new URLClassLoader(
            new URL[]{outputDir.toUri().toURL()}, getClass().getClassLoader())) {
      var resolvers = ServiceLoader.load(HelperResolver.class, loader);
      List<String> registered = new ArrayList<>();
      resolvers.forEach(resolver -> resolver.registerHelpers(
              (name, _) -> registered.add(name), clazz -> null));
      assertThat(registered).containsExactly("greetHelper");

      var fixtureClass = Class.forName("fixture.TestGreetHelper", true, loader);
      var instanceResolvers = ServiceLoader.load(HelperInstanceResolver.class, loader);
      var instances = new ArrayList<>();
      instanceResolvers.forEach(r -> instances.add(r.resolve(fixtureClass)));
      assertThat(instances).hasSize(1);
      assertThat(instances.get(0)).isInstanceOf(fixtureClass);
    }
  }

  @Test
  void processorSkipsAbstractClass() throws Exception {
    var srcDir = tempDir.resolve("src2");
    Files.createDirectories(srcDir);
    var fixtureSource = srcDir.resolve("AbstractHelper.java");
    Files.writeString(fixtureSource, """
            import cbs.nova.dsl.Context;
            import cbs.nova.dsl.Executable;
            import cbs.nova.dsl.annotation.Helper;
            import cbs.nova.dsl.Result;

            @Helper(name = "abstractHelper")
            public abstract class AbstractHelper implements Executable<String, String> {
              @Override
              public abstract Result<String> execute(Context<String> ctx);
            }
            """);

    var outputDir = tempDir.resolve("out2");
    Files.createDirectories(outputDir);
    String classpath = System.getProperty("java.class.path");

    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var fileManager = compiler.getStandardFileManager(diagnostics, null, null);
    var compilationUnits = fileManager.getJavaFileObjects(fixtureSource.toFile());
    var options = List.of(
            "-cp", classpath,
            "-processorpath", classpath,
            "-processor", HelperSpiProcessor.class.getName(),
            "-d", outputDir.toString(),
            "-source", "25",
            "-target", "25");
    var task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
    task.call();

    var resolverClass = outputDir.resolve("fixture/GeneratedHelperResolver.class");
    assertThat(resolverClass).doesNotExist();
  }

  @Test
  void processorGeneratesLazyFactoryHelper() throws Exception {
    var srcDir = tempDir.resolve("src_lazy");
    Files.createDirectories(srcDir);
    var fixtureSource = srcDir.resolve("LazyFactoryHelper.java");
    Files.writeString(fixtureSource,
            """
                    package lazyfix;

                    import cbs.nova.dsl.Context;
                    import cbs.nova.dsl.Executable;
                    import cbs.nova.dsl.Result;
                    import cbs.nova.dsl.annotation.Helper;
                    import cbs.nova.dsl.annotation.Helper.ComponentModel;
                    import cbs.nova.dsl.annotation.Helper.CreationStrategy;

                    @Helper(name = "lazyFactoryHelper", componentModel = ComponentModel.LAZY, creationStrategy = CreationStrategy.FACTORY)
                    public class LazyFactoryHelper implements Executable<String, String> {
                      @Override
                      public Result<String> execute(Context<String> ctx) {
                        return Result.success("lazy:" + ctx.body());
                      }
                    }
                    """);

    var outputDir = tempDir.resolve("out_lazy");
    Files.createDirectories(outputDir);

    String classpath = System.getProperty("java.class.path");
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var fileManager = compiler.getStandardFileManager(diagnostics, null, null);
    var compilationUnits = fileManager.getJavaFileObjects(fixtureSource.toFile());
    var options = new ArrayList<>(List.of(
            "-cp", classpath,
            "-processorpath", classpath,
            "-processor", HelperSpiProcessor.class.getName(),
            "-d", outputDir.toString(),
            "-source", "25",
            "-target", "25"));
    var task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
    boolean success = task.call();
    diagnostics.getDiagnostics()
            .forEach(d -> log.info("{}: {}", d.getKind(), d.getMessage(null)));
    assertThat(success).isTrue();

    // Inspect the generated source for the expected LAZY+FACTORY registration shape
    var generatedSource = outputDir.resolve("lazyfix/GeneratedHelperResolver.java");
    assertThat(generatedSource).exists();
    String src = Files.readString(generatedSource);
    assertThat(src)
            .contains("() -> new LazyFactoryHelper()")
            .contains("registrar.register(\"lazyFactoryHelper\"");

    // Now compile and exercise the registration through a capturing registrar
    var captureDir = tempDir.resolve("out_lazy_loaded");
    Files.createDirectories(captureDir);
    var compileOptions = List.of(
            "-cp", classpath,
            "-d", captureDir.toString(),
            "-source", "25",
            "-target", "25");
    var captureUnits = fileManager.getJavaFileObjects(generatedSource.toFile());
    var captureTask = compiler.getTask(null, fileManager, new DiagnosticCollector<>(),
            compileOptions, null, captureUnits);
    assertThat(captureTask.call()).isTrue();

    try (var loader = new URLClassLoader(
            new URL[]{captureDir.toUri().toURL()}, getClass().getClassLoader())) {
      var resolvers = ServiceLoader.load(HelperResolver.class, loader);
      var instanceResolvers = ServiceLoader.load(HelperInstanceResolver.class, loader);

      List<String> names = new ArrayList<>();
      List<Supplier<Executable<?, ?>>> suppliers = new ArrayList<>();
      HelperRegistrar registrar = new HelperRegistrar() {
        @Override
        public void register(String name, Supplier<Executable<?, ?>> helperSupplier) {
          names.add(name);
          suppliers.add(helperSupplier);
        }
      };
      resolvers.forEach(r -> r.registerHelpers(registrar, _ -> {
        throw new IllegalStateException("FACTORY helper should not call instanceResolver");
      }));

      assertThat(names).containsExactly("lazyFactoryHelper");
      assertThat(suppliers).hasSize(1);

      Executable<?, ?> resolved = suppliers.get(0).get();
      var fixtureClass = Class.forName("lazyfix.LazyFactoryHelper", true, loader);
      assertThat(resolved).isInstanceOf(fixtureClass);

      // And the generated instance resolver can still produce a fresh instance
      var produced = new ArrayList<Executable<?, ?>>();
      instanceResolvers.forEach(r -> produced.add(r.resolve(fixtureClass)));
      assertThat(produced).hasSize(1);
      assertThat(produced.get(0)).isInstanceOf(fixtureClass);
    }
  }

  @Test
  void processorHandlesSpringHelper() throws Exception {
    var srcDir = tempDir.resolve("src_spring");
    Files.createDirectories(srcDir);
    var fixtureSource = srcDir.resolve("SpringGreetHelper.java");
    Files.writeString(fixtureSource, """
            package springfix;

            import cbs.nova.dsl.Context;
            import cbs.nova.dsl.Executable;
            import cbs.nova.dsl.Result;
            import cbs.nova.starter.annotation.SpringHelper;

            @SpringHelper(name = "springGreetHelper")
            public class SpringGreetHelper implements Executable<String, String> {
              @Override
              public Result<String> execute(Context<String> ctx) {
                return Result.success("Spring, " + ctx.body());
              }
            }
            """);

    var outputDir = tempDir.resolve("out_spring");
    Files.createDirectories(outputDir);

    String classpath = compileWithStarterFallback(srcDir.resolve("SpringGreetHelper.java"),
            outputDir);

    var resolverClass = outputDir.resolve("springfix/GeneratedHelperResolver.class");
    assertThat(resolverClass).exists();

    var instanceResolverClass = outputDir
            .resolve("springfix/GeneratedHelperInstanceResolver.class");
    assertThat(instanceResolverClass).exists();

    // Inspect the generated source for the SpringHelper registration shape (LAZY+FACTORY).
    var generatedSource = outputDir.resolve("springfix/GeneratedHelperResolver.java");
    assertThat(generatedSource).exists();
    String src = Files.readString(generatedSource);
    assertThat(src)
            .contains("() -> new SpringGreetHelper()")
            .contains("registrar.register(\"springGreetHelper\"");

    try (var loader = new URLClassLoader(
            new URL[]{outputDir.toUri().toURL()}, getClass().getClassLoader())) {
      var resolvers = ServiceLoader.load(HelperResolver.class, loader);
      var instanceResolvers = ServiceLoader.load(HelperInstanceResolver.class, loader);

      List<String> names = new ArrayList<>();
      List<Supplier<Executable<?, ?>>> suppliers = new ArrayList<>();
      HelperRegistrar registrar = new HelperRegistrar() {
        @Override
        public void register(String name, Supplier<Executable<?, ?>> helperSupplier) {
          names.add(name);
          suppliers.add(helperSupplier);
        }
      };
      resolvers.forEach(r -> r.registerHelpers(registrar, _ -> {
        throw new IllegalStateException("FACTORY helper should not call instanceResolver");
      }));

      assertThat(names).containsExactly("springGreetHelper");
      Executable<?, ?> resolved = suppliers.get(0).get();
      var fixtureClass = Class.forName("springfix.SpringGreetHelper", true, loader);
      assertThat(resolved).isInstanceOf(fixtureClass);

      var produced = new ArrayList<Executable<?, ?>>();
      instanceResolvers.forEach(r -> produced.add(r.resolve(fixtureClass)));
      assertThat(produced).hasSize(1);
      assertThat(produced.get(0)).isInstanceOf(fixtureClass);
    }
  }

  /**
   * Tries to compile the given source using the system java.class.path first. If the compile fails
   * because SpringHelper is missing, retries with the starter build directory appended to the
   * classpath. Returns the classpath that produced a successful compile.
   */
  private String compileWithStarterFallback(Path source, Path outputDir) throws Exception {
    String base = System.getProperty("java.class.path");
    if (tryCompile(source, outputDir, base)) {
      return base;
    }
    Path[] candidates = new Path[]{
        Path.of("../dsl-starter/starter/build/classes/java/main"),
        Path.of("../../../dsl-starter/starter/build/classes/java/main"),
        Path.of("build/classes/java/main")
    };
    for (Path candidate : candidates) {
      if (Files.isDirectory(candidate)) {
        String extended = candidate.toAbsolutePath() + ":" + base;
        if (tryCompile(source, outputDir, extended)) {
          return extended;
        }
      }
    }
    Path[] jarCandidates = new Path[]{
        Path.of("../../../dsl-starter/starter/build/libs/starter-0.0.1-SNAPSHOT-plain.jar"),
        Path.of("build/libs/starter-0.0.1-SNAPSHOT-plain.jar")
    };
    for (Path candidate : jarCandidates) {
      if (Files.isRegularFile(candidate)) {
        String extended = candidate.toAbsolutePath() + ":" + base;
        if (tryCompile(source, outputDir, extended)) {
          return extended;
        }
      }
    }
    throw new IllegalStateException(
            "Failed to compile SpringHelper source with starter on the classpath");
  }

  private boolean tryCompile(Path source, Path outputDir, String classpath) throws Exception {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var fileManager = compiler.getStandardFileManager(diagnostics, null, null);
    var compilationUnits = fileManager.getJavaFileObjects(source.toFile());
    var options = List.of(
            "-cp", classpath,
            "-processorpath", classpath,
            "-processor", HelperSpiProcessor.class.getName(),
            "-d", outputDir.toString(),
            "-source", "25",
            "-target", "25");
    var task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
    boolean success = task.call();
    diagnostics.getDiagnostics()
            .forEach(d -> System.out.println(d.getKind() + ": " + d.getMessage(null)));
    return success;
  }
}
