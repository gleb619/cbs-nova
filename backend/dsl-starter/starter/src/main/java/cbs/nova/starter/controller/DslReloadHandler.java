package cbs.nova.starter.controller;

import cbs.nova.dsl.DslCompactSource;
import cbs.nova.dsl.DslDefinitionLoader;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.LoadResult;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.helper.HelperResolver;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.ReloadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Functional handler for the DSL reload endpoint. Registered as a {@code RouterFunction} bean by
 * {@link cbs.nova.starter.config.DslReloadRouterConfiguration} (gated by
 * {@code dsl.reload.enabled}, on by default) rather than as a hardcoded {@code @RestController}, so
 * host applications can opt out of exposing it.
 *
 * <h2>Failure semantics</h2> Reload is failure-safe: the live {@link GlobalManager} singleton is
 * only replaced once the newly-compiled DSL set has been built and staged against a throwaway
 * candidate. If compilation or staging throws, the previously-loaded registry keeps serving
 * requests — the runtime is never bricked.
 *
 * <h2>Concurrency</h2> A {@link ReentrantLock} serializes overlapping reload calls. Policy: the
 * second (and any further) concurrent caller <em>waits</em> for the first to complete and then runs
 * against the (possibly already-updated) registry. We deliberately do not return 409 here, so
 * callers in pipelines (workbench publish, CI) get a deterministic outcome rather than a transient
 * rejection that they have to retry.
 */
@Component
@ConditionalOnProperty(prefix = "dsl.reload", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class DslReloadHandler {

  private static final String RELOAD_TEMP_PREFIX = "dsl-reload-";

  private final DslProperties dslProperties;
  private final DslDefinitionLoader loader;
  private final ReentrantLock reloadLock = new ReentrantLock();

  public DslReloadHandler(DslProperties dslProperties, DslDefinitionLoader loader) {
    this.dslProperties = dslProperties;
    this.loader = loader;
  }

  /**
   * Reloads DSL definitions from the configured source directory using a dedicated classloader and
   * the SPI mechanisms {@link cbs.nova.dsl.DslDefinitionProvider} and {@link HelperResolver}.
   * Responds 200 with a {@link ReloadResponse} carrying the {@link LoadResult} drilldown of what
   * was loaded.
   */
  public ServerResponse reload(ServerRequest request) throws IOException {
    var sourceDirProperty = dslProperties.getSourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return error(HttpStatus.CONFLICT, new ErrorResponse(
              "NOT_CONFIGURED", "dsl.source-dir is not configured", null, null, null));
    }
    var dir = Path.of(sourceDirProperty);
    if (!Files.isDirectory(dir)) {
      return error(HttpStatus.CONFLICT, new ErrorResponse(
              "NOT_FOUND", "Source directory does not exist: " + dir, null, null, null));
    }

    reloadLock.lock();
    try {
      var load = doReload(dir);
      return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(new ReloadResponse(dir.toString(), load));
    } catch (Exception e) {
      log.error("[DSL reload] Failed to reload DSL definitions from {}", dir, e);
      return error(HttpStatus.INTERNAL_SERVER_ERROR,
              new ErrorResponse("RELOAD_FAILED", e.getMessage(), null, null, null));
    } finally {
      reloadLock.unlock();
    }
  }

  /**
   * Programmatic reload used by callers that are already inside a request flow and want the
   * {@link LoadResult} drilldown directly rather than a {@link ServerResponse} (workbench draft
   * publish). Shares the same {@link ReentrantLock} serialization and failure semantics as
   * {@link #reload(ServerRequest)}: throws on compile/staging failure, leaving the live registry
   * untouched.
   */
  public LoadResult reloadDefinitions() throws IOException {
    var sourceDirProperty = dslProperties.getSourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      throw new IllegalStateException("dsl.source-dir is not configured");
    }
    var dir = Path.of(sourceDirProperty);
    if (!Files.isDirectory(dir)) {
      throw new IllegalStateException("Source directory does not exist: " + dir);
    }

    reloadLock.lock();
    try {
      return doReload(dir);
    } finally {
      reloadLock.unlock();
    }
  }

  private LoadResult doReload(Path sourceDir) throws IOException {
    var parent = Thread.currentThread().getContextClassLoader();
    Path outputDir = null;
    URLClassLoader reloadClassLoader = null;
    try {
      outputDir = compileJavaSources(sourceDir);
      reloadClassLoader = new URLClassLoader(
              new URL[]{sourceDir.toUri().toURL(), outputDir.toUri().toURL()}, parent);
      Thread.currentThread().setContextClassLoader(reloadClassLoader);

      // Build the new DSL set into a throwaway candidate GlobalManager. The live
      // singleton is not touched until the staging has fully succeeded.
      var candidate = DslConfig.dslConfig().globalManager();
      var load = loadDefinitions(reloadClassLoader, sourceDir, outputDir, candidate);

      // Atomic swap — only after every registration above has succeeded.
      GlobalManager.globalManager().replaceGlobalManager(candidate);
      return load;
    } finally {
      Thread.currentThread().setContextClassLoader(parent);
      if (reloadClassLoader != null) {
        try {
          reloadClassLoader.close();
        } catch (IOException e) {
          log.warn("[DSL reload] Failed to close reload classloader: {}", e.getMessage());
        }
      }
      deleteRecursively(outputDir);
    }
  }

  private LoadResult loadDefinitions(ClassLoader classLoader, Path sourceDir, Path outputDir,
          GlobalManager target) {
    var result = LoadResult.builder();

    // Prefer SPI-based DslDefinitionProvider definitions.
    result.merge(loader.load(classLoader, target));

    // Load helper resolvers via SPI.
    var instanceResolver = helperInstanceResolver();
    if (instanceResolver != null) {
      ServiceLoader.load(HelperResolver.class, classLoader)
              .forEach(resolver -> resolver.registerHelpers(target::registerHelper,
                      instanceResolver));
    }

    // Fallback for compact-source DSL files that implement DslCompactSource directly.
    loadCompactSources(classLoader, sourceDir, outputDir, target, result);

    var load = result.build();
    log.info("[DSL reload] Loaded {} DSL definitions from {}: processes={}, transactions={},"
            + " functions={}",
            load.total(), sourceDir, load.processCount(), load.transactionCount(),
            load.functionCount());
    return load;
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
          GlobalManager target, LoadResult.Builder result) {
    List<String> classNames = collectClassNames(outputDir);
    for (String className : classNames) {
      try {
        Class<?> clazz = classLoader.loadClass(className);
        if (!DslCompactSource.class.isAssignableFrom(clazz)) {
          continue;
        }
        var instance = (DslCompactSource) clazz.getDeclaredConstructor().newInstance();
        for (DslObject obj : instance.define()) {
          register(obj, target, result);
        }
      } catch (Exception e) {
        log.warn("[DSL reload] Could not load compact source {}: {}", className, e.getMessage(), e);
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
      log.warn("[DSL reload] Failed to scan compiled classes: {}", e.getMessage(), e);
    }
    return classNames;
  }

  private void register(DslObject obj, GlobalManager target, LoadResult.Builder result) {
    switch (obj.type()) {
      case PROCESS -> {
        target.registerProcess((ProcessDslObject) obj);
        result.add(DslObject.DslType.PROCESS, obj.name());
      }
      case TRANSACTION -> {
        target.registerTransaction((TransactionDslObject) obj);
        result.add(DslObject.DslType.TRANSACTION, obj.name());
      }
      case FUNCTION -> {
        target.registerFunction((FunctionDslObject) obj);
        result.add(DslObject.DslType.FUNCTION, obj.name());
      }
    }
  }

  private static void deleteRecursively(Path dir) {
    if (dir == null) {
      return;
    }
    try (Stream<Path> stream = Files.walk(dir)) {
      stream.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (NoSuchFileException e) {
          // already gone — fine
        } catch (IOException e) {
          log.warn("[DSL reload] Failed to delete temp path {}: {}", p, e.getMessage());
        }
      });
    } catch (IOException e) {
      log.warn("[DSL reload] Failed to walk temp dir {} for cleanup: {}", dir, e.getMessage());
    }
  }

  private static ServerResponse error(HttpStatus status, ErrorResponse body) throws IOException {
    return ServerResponse.status(status).body(body);
  }
}
