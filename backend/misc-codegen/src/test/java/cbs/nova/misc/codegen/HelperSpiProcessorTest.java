package cbs.nova.misc.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.HelperInstanceResolver;
import cbs.nova.dsl.HelperResolver;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

@Slf4j
class HelperSpiProcessorTest {

  @TempDir
  Path tempDir;

  @Mock
  HelperInstanceResolver helperInstanceResolver;

  @Test
  void processorGeneratesResolverForValidHelper() throws Exception {
    var srcDir = tempDir.resolve("src/fixture");
    Files.createDirectories(srcDir);
    var fixtureSource = srcDir.resolve("TestGreetHelper.java");
    Files.writeString(fixtureSource, """
            package fixture;

            import cbs.nova.dsl.Context;
            import cbs.nova.dsl.Executable;
            import cbs.nova.dsl.Helper;
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

    var spiFile = outputDir.resolve("META-INF/services/cbs.nova.dsl.HelperResolver");
    assertThat(spiFile).exists();
    assertThat(Files.readString(spiFile).strip())
            .isEqualTo("fixture.GeneratedHelperResolver");

    try (var loader = new URLClassLoader(
            new URL[]{outputDir.toUri().toURL()}, getClass().getClassLoader())) {
      Class<?> resolverCls = loader.loadClass("fixture.GeneratedHelperResolver");
      HelperResolver resolver = (HelperResolver) resolverCls.getDeclaredConstructor().newInstance();
      List<String> registered = new ArrayList<>();
      resolver.registerHelpers((name, _) -> registered.add(name), helperInstanceResolver);
      assertThat(registered).containsExactly("greetHelper");
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
            import cbs.nova.dsl.Helper;
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
}
