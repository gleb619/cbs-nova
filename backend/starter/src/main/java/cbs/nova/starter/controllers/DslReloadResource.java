package cbs.nova.starter.controllers;

import cbs.nova.dsl.DslCompactSource;
import cbs.nova.dsl.DslDefinitionLoader;
import cbs.nova.dsl.DslDefinitionProvider;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.helper.HelperResolver;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.models.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * Functional handler for the DSL reload endpoint. Registered as a {@code RouterFunction} bean by
 * {@link cbs.nova.starter.config.DslReloadRouterConfiguration} (gated by
 * {@code dsl.reload.enabled}, on by default) rather than as a hardcoded {@code @RestController}, so
 * host applications can opt out of exposing it.
 */
@Component
@ConditionalOnProperty(prefix = "dsl.reload", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class DslReloadResource {

  private static final String RELOAD_TEMP_PREFIX = "dsl-reload-";

  private final DslProperties dslProperties;
  private final DslDefinitionLoader loader;

  public DslReloadResource(DslProperties dslProperties, DslDefinitionLoader loader) {
    this.dslProperties = dslProperties;
    this.loader = loader;
  }

  /**
   * Reloads DSL definitions from the configured source directory using a dedicated classloader and
   * the SPI mechanisms {@link DslDefinitionProvider} and {@link HelperResolver}.
   */
  public ServerResponse reload(ServerRequest request) throws IOException {
    var sourceDirProperty = dslProperties.sourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return error(HttpStatus.CONFLICT, new ErrorResponse(
              "NOT_CONFIGURED", "dsl.source-dir is not configured", null, null, null));
    }
    var dir = Path.of(sourceDirProperty);
    if (!Files.isDirectory(dir)) {
      return error(HttpStatus.CONFLICT, new ErrorResponse(
              "NOT_FOUND", "Source directory does not exist: " + dir, null, null, null));
    }
    GlobalManager.globalManager().resetForTests();
    try {
      doReload(dir);
      return ServerResponse.noContent().build();
    } catch (Exception e) {
      log.error("[DSL reload] Failed to reload DSL definitions from {}", dir, e);
      return error(HttpStatus.INTERNAL_SERVER_ERROR,
              new ErrorResponse("RELOAD_FAILED", e.getMessage(), null, null, null));
    }
  }

  private void doReload(Path sourceDir) throws IOException {
    var parent = Thread.currentThread().getContextClassLoader();
    var outputDir = compileJavaSources(sourceDir);
    var reloadClassLoader = new URLClassLoader(
            new URL[]{sourceDir.toUri().toURL(), outputDir.toUri().toURL()}, parent);
    try {
      Thread.currentThread().setContextClassLoader(reloadClassLoader);
      loadDefinitions(reloadClassLoader, sourceDir, outputDir);
    } finally {
      Thread.currentThread().setContextClassLoader(parent);
    }
  }

  private void loadDefinitions(ClassLoader classLoader, Path sourceDir, Path outputDir) {
    var gm = GlobalManager.globalManager();

    // Prefer SPI-based DslDefinitionProvider definitions.
    loader.load(classLoader, gm);

    // Load helper resolvers via SPI.
    var instanceResolver = helperInstanceResolver();
    if (instanceResolver != null) {
      ServiceLoader.load(HelperResolver.class, classLoader)
              .forEach(resolver -> resolver.registerHelpers(
                      (name, helper) -> gm.registerHelper(name, helper), instanceResolver));
    }

    // Fallback for compact-source DSL files that implement DslCompactSource directly.
    loadCompactSources(classLoader, sourceDir, outputDir, gm);
  }

  private HelperInstanceResolver helperInstanceResolver() {
    try {
      return DslConfig.dslConfig().helperInstanceResolver().get();
    } catch (Exception e) {
      log.warn(
              "[DSL reload] HelperInstanceResolver not available, skipping HelperResolver loading");
      return null;
    }
  }

  private Path compileJavaSources(Path sourceDir) throws IOException {
    var outputDir = Files.createTempDirectory(RELOAD_TEMP_PREFIX);
    List<Path> javaFiles;
    try (Stream<Path> stream = Files.walk(sourceDir)) {
      javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
    }
    if (javaFiles.isEmpty()) {
      return outputDir;
    }
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No system Java compiler available (JDK required)");
    }
    var classpath = System.getProperty("java.class.path");
    try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
      var options = List.of("-classpath", classpath, "-d", outputDir.toString());
      for (var file : javaFiles) {
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var unit = fm.getJavaFileObjectsFromFiles(List.of(file.toFile()));
        var task = compiler.getTask(null, fm, diagnostics, options, null, unit);
        if (!task.call()) {
          diagnostics.getDiagnostics().forEach(d -> log.error(
                  "[DSL reload] {}: {}", file.getFileName(), d.getMessage(null)));
          throw new IllegalStateException("Failed to compile DSL source: " + file.getFileName());
        }
      }
    }
    return outputDir;
  }

  private void loadCompactSources(ClassLoader classLoader, Path sourceDir, Path outputDir,
          GlobalManager gm) {
    List<String> classNames = collectClassNames(outputDir);
    for (String className : classNames) {
      try {
        Class<?> clazz = classLoader.loadClass(className);
        if (!DslCompactSource.class.isAssignableFrom(clazz)) {
          continue;
        }
        var instance = (DslCompactSource) clazz.getDeclaredConstructor().newInstance();
        for (DslObject obj : instance.define()) {
          register(obj, gm);
        }
      } catch (Exception e) {
        log.warn("[DSL reload] Could not load compact source {}: {}", className, e.getMessage());
      }
    }
  }

  private List<String> collectClassNames(Path outputDir) {
    var classNames = new ArrayList<String>();
    if (!Files.isDirectory(outputDir)) {
      return classNames;
    }
    try (Stream<Path> stream = Files.walk(outputDir)) {
      stream.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
        String relative = outputDir.relativize(p).toString()
                .replace(".class", "")
                .replace("/", ".")
                .replace("\\", ".");
        classNames.add(relative);
      });
    } catch (IOException e) {
      log.warn("[DSL reload] Failed to scan compiled classes: {}", e.getMessage());
    }
    return classNames;
  }

  private void register(DslObject obj, GlobalManager gm) {
    switch (obj.type()) {
      case PROCESS -> gm.registerProcess((ProcessDslObject) obj);
      case TRANSACTION -> gm.registerTransaction((TransactionDslObject) obj);
      case FUNCTION -> gm.registerFunction((FunctionDslObject) obj);
    }
  }

  private static ServerResponse error(HttpStatus status, ErrorResponse body) throws IOException {
    return ServerResponse.status(status).body(body);
  }
}
