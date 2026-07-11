package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DslDefinitionProvider;
import cbs.nova.dsl.DslObject;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Compiles compact DSL source files at build time and loads the resulting definitions through a
 * generated {@link DslDefinitionProvider}.
 *
 * <p>
 * Each {@code .java} file is compiled individually so that a single uncompilable file does not
 * abort the whole batch. After successful compilation, a tiny provider class is generated and
 * compiled on the spot (and registered via {@code META-INF/services}) so the definitions can be
 * discovered with {@link ServiceLoader}.
 */
@Slf4j
public final class DslSourceCompiler {

  public @NonNull List<DslObject> compileAndLoad(@NonNull Path sourceDir) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No system Java compiler available (JDK required)");
    }

    var result = new ArrayList<DslObject>();
    var outputDir = Files.createTempDirectory("dsl-codegen-");
    var classpath = System.getProperty("java.class.path");

    List<Path> javaFiles;
    try (var stream = Files.walk(sourceDir)) {
      javaFiles = stream
              .filter(p -> p.toString().endsWith(".java"))
              .toList();
    }
    if (javaFiles.isEmpty()) {
      return result;
    }

    var compiledClassNames = new ArrayList<String>();
    try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
      var options = List.of("-classpath", classpath, "-d", outputDir.toString());
      for (var file : javaFiles) {
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var singleUnit = fm.getJavaFileObjectsFromFiles(List.of(file.toFile()));
        var task = compiler.getTask(null, fm, diagnostics, options, null, singleUnit);
        boolean ok;
        try {
          ok = task.call();
        } catch (Exception e) {
          log.error("[DslSourceCompiler] Compilation of {} failed: {}",
                  file.getFileName(), e.getMessage(), e);
          continue;
        }
        if (!ok) {
          diagnostics.getDiagnostics().forEach(d -> log.error(
                  "[DslSourceCompiler] {}: {}", file.getFileName(), d.getMessage(null)));
          continue;
        }
        var name = file.getFileName().toString();
        compiledClassNames.add(name.substring(0, name.length() - ".java".length()));
      }
    }

    if (compiledClassNames.isEmpty()) {
      return result;
    }

    new DefinitionProviderGenerator().generate(outputDir, compiledClassNames);
    var providerSource = outputDir.resolve(DefinitionProviderGenerator.PROVIDER_CLASS + ".java");
    compileProvider(compiler, providerSource, classpath, outputDir);
    return loadProvider(outputDir);
  }

  private void compileProvider(JavaCompiler compiler, Path providerSource,
          String classpath, Path outputDir) throws IOException {
    try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
      var diagnostics = new DiagnosticCollector<JavaFileObject>();
      var unit = fm.getJavaFileObjectsFromFiles(List.of(providerSource.toFile()));
      var providerClasspath = classpath + File.pathSeparator + outputDir.toString();
      var options = List.of("-classpath", providerClasspath, "-d", outputDir.toString());
      var task = compiler.getTask(null, fm, diagnostics, options, null, unit);
      boolean ok = task.call();
      if (!ok) {
        diagnostics.getDiagnostics().forEach(
                d -> log.error("[DslSourceCompiler] provider: {}", d.getMessage(null)));
        throw new IllegalStateException("Failed to compile generated DSL definition provider");
      }
    }
  }

  private List<DslObject> loadProvider(Path outputDir) {
    URL url;
    try {
      url = outputDir.toUri().toURL();
    } catch (IOException e) {
      log.error("[DslSourceCompiler] Failed to build provider URL: {}", e.getMessage(), e);
      return List.of();
    }
    try (var loader = new URLClassLoader(new URL[]{url},
            Thread.currentThread().getContextClassLoader())) {
      var providers = ServiceLoader.load(DslDefinitionProvider.class, loader);
      for (var provider : providers) {
        return provider.definitions();
      }
      log.error("[DslSourceCompiler] No DslDefinitionProvider found in {}", outputDir);
    } catch (Exception e) {
      log.error("[DslSourceCompiler] Failed to load DSL definitions: {}", e.getMessage(), e);
    }
    return List.of();
  }
}
