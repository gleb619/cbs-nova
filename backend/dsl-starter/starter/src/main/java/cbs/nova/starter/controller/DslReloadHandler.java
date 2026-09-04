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
import cbs.nova.starter.config.router.DslReloadRouterConfiguration;
import cbs.nova.starter.exception.DslCompilationException;
import cbs.nova.starter.model.CompileDiagnostic;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.ReloadResponse;
import cbs.nova.starter.service.PreviewResultCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import javax.tools.Diagnostic;
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
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Functional handler for the DSL reload endpoint. Registered as a {@code RouterFunction} bean by
 * {@link DslReloadRouterConfiguration} (gated by {@code dsl.reload.enabled}, on by default) rather
 * than as a hardcoded {@code @RestController}, so host applications can opt out of exposing it.
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
@ConditionalOnProperty(prefix = "csb.dsl.reload", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class DslReloadHandler {

  private static final String RELOAD_TEMP_PREFIX = "dsl-reload-";
  private static final int DIAGNOSTIC_CAP = 20;

  private final DslProperties dslProperties;
  private final DslDefinitionLoader loader;
  private final ObjectProvider<PreviewResultCache> previewCacheProvider;
  private final ReentrantLock reloadLock = new ReentrantLock();

  /**
   * Spring-injected constructor. The {@link PreviewResultCache} bean is resolved through an
   * {@link ObjectProvider} so the reload path stays usable when the cache is absent (e.g. in tests
   * that don't wire the starter preview cache, or when a host disables preview caching).
   */
  @Autowired
  public DslReloadHandler(DslProperties dslProperties, DslDefinitionLoader loader,
          ObjectProvider<PreviewResultCache> previewCacheProvider) {
    this.dslProperties = dslProperties;
    this.loader = loader;
    this.previewCacheProvider = previewCacheProvider;
  }

  /**
   * Backwards-compatible constructor for tests and direct instantiation: builds a handler with no
   * preview cache flush wired in. Delegates to the Spring constructor with a {@code null} provider.
   */
  public DslReloadHandler(DslProperties dslProperties, DslDefinitionLoader loader) {
    this(dslProperties, loader, null);
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
              "NOT_CONFIGURED", "csb.dsl.source-dir is not configured", null, null, null));
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
      if (e instanceof DslCompilationException dce) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, new ErrorResponse(
                "RELOAD_FAILED", dce.getMessage(), null, null, null, dce.diagnostics()));
      }
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
      throw new IllegalStateException("csb.dsl.source-dir is not configured");
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
    Path outputDir = Files.createTempDirectory(RELOAD_TEMP_PREFIX);
    URLClassLoader reloadClassLoader = null;
    try {
      compileJavaSources(sourceDir, outputDir);
      reloadClassLoader = new URLClassLoader(
              new URL[]{sourceDir.toUri().toURL(), outputDir.toUri().toURL()}, parent);
      Thread.currentThread().setContextClassLoader(reloadClassLoader);

      // Build the new DSL set into a throwaway candidate GlobalManager. The live
      // singleton is not touched until the staging has fully succeeded.
      var candidate = DslConfig.dslConfig().globalManager();
      var load = loadDefinitions(reloadClassLoader, sourceDir, outputDir, candidate);

      // Atomic swap — only after every registration above has succeeded.
      GlobalManager.globalManager().replaceGlobalManager(candidate);
      // Preview results cached against the previous registry are now stale; flush after the swap
      // so a failed compile/staging leaves the cache (and the live registry) untouched.
      flushPreviewCache();
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

  /**
   * Drops every cached preview result, so the next preview call recomputes against the freshly
   * swapped registry instead of returning a stale hit. No-op when the cache bean is absent (handler
   * built without a provider) or when the provider yields no bean (cache disabled).
   */
  private void flushPreviewCache() {
    if (previewCacheProvider == null) {
      return;
    }
    var cache = previewCacheProvider.getIfAvailable();
    if (cache == null) {
      return;
    }
    cache.clear();
    log.info("[DSL reload] preview cache flushed after registry swap");
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
    log.info(
            "[DSL reload] Loaded {} DSL definitions from {}: processes={}, transactions={},"
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

  private void compileJavaSources(Path sourceDir, Path outputDir) throws IOException {
    List<Path> javaFiles;
    try (Stream<Path> stream = Files.walk(sourceDir)) {
      javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
    }
    if (javaFiles.isEmpty()) {
      return;
    }
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No system Java compiler available (JDK required)");
    }
    var classpath = System.getProperty("java.class.path");
    var collected = new ArrayList<CompileDiagnostic>();
    Path firstFailedFile = null;
    try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
      var options = List.of("-classpath", classpath, "-d", outputDir.toString());
      for (var file : javaFiles) {
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var unit = fm.getJavaFileObjectsFromFiles(List.of(file.toFile()));
        var task = compiler.getTask(null, fm, diagnostics, options, null, unit);
        if (!task.call()) {
          if (firstFailedFile == null) {
            firstFailedFile = file;
          }
          for (var d : diagnostics.getDiagnostics()) {
            if (collected.size() >= DIAGNOSTIC_CAP) {
              break;
            }
            collected.add(toCompileDiagnostic(d, file));
          }
        }
      }
    }
    if (!collected.isEmpty()) {
      throw new DslCompilationException(
              "Failed to compile DSL source: " + firstFailedFile.getFileName(), collected);
    }
  }

  private static CompileDiagnostic toCompileDiagnostic(Diagnostic<? extends JavaFileObject> d,
          Path file) {
    var source = d.getSource();
    var sourceName = source != null ? source.getName() : file.getFileName().toString();
    var line = d.getLineNumber() == Diagnostic.NOPOS ? null : Long.valueOf(d.getLineNumber());
    var column = d.getColumnNumber() == Diagnostic.NOPOS
            ? null
            : Long.valueOf(d.getColumnNumber());
    var severity = switch (d.getKind()) {
      case WARNING, MANDATORY_WARNING -> "warning";
      default -> "error";
    };
    return new CompileDiagnostic(sourceName, line, column,
            d.getMessage(Locale.getDefault()), severity);
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
    List<Path> paths;
    try (Stream<Path> stream = Files.walk(dir)) {
      paths = stream.sorted((a, b) -> -a.compareTo(b)).toList();
    } catch (IOException e) {
      log.warn("[DSL reload] Failed to walk temp dir {} for cleanup: {}", dir, e.getMessage());
      return;
    }
    for (Path p : paths) {
      try {
        Files.deleteIfExists(p);
      } catch (NoSuchFileException e) {
        // already gone — fine
      } catch (IOException e) {
        log.warn("[DSL reload] Failed to delete temp path {}: {}", p, e.getMessage());
      }
    }
  }

  private static ServerResponse error(HttpStatus status, ErrorResponse body) throws IOException {
    return ServerResponse.status(status).body(body);
  }

}
