package cbs.nova.starter.controller;

import static cbs.nova.starter.core.StarterConstants.DSL_RELOAD_TEMP_PREFIX;

import cbs.nova.dsl.DslCompactSource;
import cbs.nova.dsl.utils.DslDefinitionLoader;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.model.LoadResult;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.helper.HelperResolver;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.starter.exception.BuilderClientBusyException;
import cbs.nova.starter.config.GlobalManagerReplacedListener;
import cbs.nova.starter.exception.BuilderUnavailableException;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.config.router.DslReloadRouterConfiguration;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.exception.DslCompilationException;
import cbs.nova.dsl.model.CompileDiagnostic;
import cbs.nova.starter.model.CompileDiagnosticSource;
import cbs.nova.starter.model.CompileModels.CompileRequest;
import cbs.nova.starter.model.CompileModels.CompileResult;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.model.ReloadResponse;
import cbs.nova.starter.persistence.CompileDiagnosticRecordRepository;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.service.DomainEventPublisher;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.PreviewResultCache;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

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
 * <h2>Compilation</h2> Sources are always compiled remotely by the dsl-builder service (via the
 * {@link DslBuilderClient} bean, gated by {@code cbs.dsl.builder-client.enabled}, on by default)
 * and the generated classes are downloaded as a zip. The builder client is mandatory: if the bean
 * is absent, reload fails fast with an {@link IllegalStateException} instead of degrading to an
 * in-process compile.
 *
 * <h2>Concurrency</h2> A {@link ReentrantLock} serializes overlapping reload calls. Policy: the
 * second (and any further) concurrent caller <em>waits</em> for the first to complete and then runs
 * against the (possibly already-updated) registry. We deliberately do not return 409 here, so
 * callers in pipelines (workbench publish, CI) get a deterministic outcome rather than a transient
 * rejection that they have to retry.
 */
@Component
@ConditionalOnProperty(prefix = "cbs.dsl.reload", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DslReloadHandler {

  private final DslProperties dslProperties;
  private final DslDefinitionLoader loader;
  private final ObjectProvider<PreviewResultCache> previewCacheProvider;
  private final ObjectProvider<DslAuditService> auditServiceProvider;
  private final ObjectProvider<DslBuilderClient> builderClientProvider;
  private final ObjectProvider<CompileDiagnosticRecordRepository> compileDiagnosticRepositoryProvider;
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;
  private final ObjectProvider<GlobalManagerReplacedListener> globalManagerReplacedListeners;
  private final ReentrantLock reloadLock = new ReentrantLock();

  /**
   * Reloads DSL definitions from the configured source directory using a dedicated classloader and
   * the SPI mechanisms {@link cbs.nova.dsl.DslDefinitionProvider} and {@link HelperResolver}.
   * Responds 200 with a {@link ReloadResponse} carrying the {@link LoadResult} drilldown of what
   * was loaded.
   */
  public ServerResponse reload(ServerRequest request) throws IOException {
    var sourceDirProperty = dslProperties.sourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      audit(request, "-", StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "NOT_CONFIGURED: cbs.dsl.source-dir is not configured"));
      return error(HttpStatus.CONFLICT, new ErrorResponse("NOT_CONFIGURED",
              "cbs.dsl.source-dir is not configured", null, null, null, null, null, null, null));
    }
    var dir = Path.of(sourceDirProperty);
    if (!Files.isDirectory(dir)) {
      audit(request, dir.toString(), StarterConstants.OUTCOME_FAILURE,
              Map.of("error", "NOT_FOUND: Source directory does not exist: " + dir));
      return error(HttpStatus.CONFLICT, new ErrorResponse("NOT_FOUND",
              "Source directory does not exist: " + dir, null, null, null, null, null, null, null));
    }

    reloadLock.lock();
    try {
      var load = doReload(dir);
      audit(request, dir.toString(), StarterConstants.OUTCOME_SUCCESS, Map.of(
              "processes", load.processCount(),
              "transactions", load.transactionCount(),
              "functions", load.functionCount(),
              "total", load.total()));
      return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(new ReloadResponse(dir.toString(), load));
    } catch (Exception e) {
      audit(request, dir.toString(), StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      log.error("[DSL reload] Failed to reload DSL definitions from {}", dir, e);
      // T411: best-effort ReloadFailed event. Reload has no DB transaction (the live
      // GlobalManager stays untouched on compile failure), so log-and-continue on a publish
      // failure rather than fabricate a TX or block the operator-facing error response.
      String reloadErr = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
      String source = dir.toString();
      publishReloadFailedBestEffort(request, source, reloadErr);
      if (e instanceof DslCompilationException dce) {
        recordDiagnostics(CompileDiagnosticSource.RELOAD, dir.toString(), dce.diagnostics());
        var responseDiagnostics = dce.diagnostics().stream().limit(20).toList();
        return error(HttpStatus.INTERNAL_SERVER_ERROR, new ErrorResponse("RELOAD_FAILED",
                dce.getMessage(), null, null, null, null, responseDiagnostics, null, null));
      }
      return error(HttpStatus.INTERNAL_SERVER_ERROR,
              new ErrorResponse("RELOAD_FAILED", e.getMessage(), null, null, null, null, null, null,
                      null));
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
    var sourceDirProperty = dslProperties.sourceDir();
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      throw new IllegalStateException("cbs.dsl.source-dir is not configured");
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
    Path outputDir = Files.createTempDirectory(DSL_RELOAD_TEMP_PREFIX);
    URLClassLoader reloadClassLoader = null;
    try {
      compileSources(sourceDir, outputDir);
      reloadClassLoader = new URLClassLoader(
              new URL[]{sourceDir.toUri().toURL(), outputDir.toUri().toURL()}, parent);
      Thread.currentThread().setContextClassLoader(reloadClassLoader);

      // Build the new DSL set into a throwaway candidate GlobalManager. The live
      // singleton is not touched until the staging has fully succeeded.
      var candidate = DslConfig.dslConfig().globalManager();
      var load = loadDefinitions(reloadClassLoader, sourceDir, outputDir, candidate);

      // Atomic swap — only after every registration above has succeeded.
      GlobalManager.globalManager().replaceGlobalManager(candidate);
      notifyGlobalManagerReplaced();
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

  private void compileSources(Path sourceDir, Path outputDir) throws IOException {
    var builder = builderClient();
    if (builder == null) {
      throw new IllegalStateException(
              "DSL reload requires a DslBuilderClient bean but none is available; in-process"
                      + " javac compilation was removed (T570). Enable"
                      + " cbs.dsl.builder-client.enabled (default) and ensure the dsl-builder"
                      + " service is configured.");
    }
    var sources = collectSources(sourceDir);
    if (sources.isEmpty()) {
      return;
    }
    var request = new CompileRequest(null, null, null, null, null, null, sources);
    CompileResult result = compileRemotely(builder, request);
    if (!result.success()) {
      throw new DslCompilationException("DSL compilation failed",
              toDiagnostics(result.diagnostics()));
    }
    byte[] zip = downloadRemotely(builder, result.id());
    extractZip(zip, outputDir);
  }

  private CompileResult compileRemotely(DslBuilderClient builder, CompileRequest request) {
    try {
      return builder.compile(request);
    } catch (BuilderUnavailableException | BuilderClientBusyException e) {
      throw new DslCompilationException("DSL builder unavailable: " + e.getMessage(), List.of());
    }
  }

  private byte[] downloadRemotely(DslBuilderClient builder, String compileId) {
    try {
      return builder.downloadZip(compileId);
    } catch (BuilderUnavailableException | BuilderClientBusyException e) {
      throw new DslCompilationException("DSL builder unavailable: " + e.getMessage(), List.of());
    }
  }

  private DslBuilderClient builderClient() {
    return builderClientProvider == null ? null : builderClientProvider.getIfAvailable();
  }

  private static Map<String, String> collectSources(Path sourceDir) throws IOException {
    Map<String, String> sources = new LinkedHashMap<>();
    try (Stream<Path> stream = Files.walk(sourceDir)) {
      for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
        sources.put(sourceDir.relativize(file).toString().replace('\\', '/'),
                Files.readString(file));
      }
    }
    return sources;
  }

  private static List<CompileDiagnostic> toDiagnostics(List<String> messages) {
    if (messages == null) {
      return List.of();
    }
    return messages.stream()
            .map(message -> new CompileDiagnostic(null, null, null, message, "error", null))
            .toList();
  }

  private static void extractZip(byte[] zip, Path outputDir) throws IOException {
    try (var input = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        Path target = outputDir.resolve(entry.getName()).normalize();
        if (!target.startsWith(outputDir)) {
          continue;
        }
        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  /**
   * Drops every cached preview result, so the next preview call recomputes against the freshly
   * swapped registry instead of returning a stale hit. No-op when the cache bean is absent (handler
   * built without a provider) or when the provider yields no bean (cache disabled).
   */

  private void notifyGlobalManagerReplaced() {
    GlobalManager globalManager = GlobalManager.globalManager();
    if (globalManagerReplacedListeners == null) {
      return;
    }
    for (GlobalManagerReplacedListener listener : globalManagerReplacedListeners) {
      try {
        listener.onGlobalManagerReplaced(globalManager);
      } catch (Exception e) {
        log.warn("[DSL reload] GlobalManagerReplacedListener failed: {}", e.getMessage(), e);
      }
    }
  }

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

  private void audit(ServerRequest request, String target, String outcome, Object details) {
    if (auditServiceProvider == null) {
      return;
    }
    var auditService = auditServiceProvider.getIfAvailable();
    if (auditService == null) {
      return;
    }
    auditService.record(DslAuditService.currentActor(), StarterConstants.ACTION_DEFINITION_RELOAD,
            target,
            DslAuditService.correlationIdOf(request), outcome, details);
  }

  /**
   * Best-effort publish of a {@link DomainEvent.ReloadFailed} event. Reload has no DB transaction
   * (the live registry stays untouched on compile failure) so we log-and-swallow on publish
   * failure: the operator-facing error response is what must reach the client. See the loop note in
   * docs/plans/T411.
   */
  private void publishReloadFailedBestEffort(ServerRequest request, String source, String error) {
    var publisher = eventPublisherProvider == null
            ? null
            : eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      return;
    }
    String correlationId = null;
    try {
      correlationId = DslAuditService.correlationIdOf(request);
    } catch (RuntimeException ignored) {
      // fall back to null
    }
    try {
      publisher.publish(new DomainEvent.ReloadFailed(null, source, error, null, correlationId));
    } catch (RuntimeException e) {
      log.warn("[DSL events] best-effort publish of ReloadFailed for source '{}' failed: {}",
              source, e.getMessage());
    }
  }

  private void recordDiagnostics(CompileDiagnosticSource source, String definition,
          List<CompileDiagnostic> diagnostics) {
    if (compileDiagnosticRepositoryProvider == null) {
      return;
    }
    var repository = compileDiagnosticRepositoryProvider.getIfAvailable();
    if (repository == null) {
      return;
    }
    try {
      repository.insertAll(source, definition, diagnostics);
    } catch (RuntimeException e) {
      log.warn("[DSL diagnostics] failed to persist compile diagnostics from {} for {}: {}",
              source, definition, e.getMessage());
    }
  }

  private static ServerResponse error(HttpStatus status, ErrorResponse body) throws IOException {
    return ServerResponse.status(status).body(body);
  }

}
