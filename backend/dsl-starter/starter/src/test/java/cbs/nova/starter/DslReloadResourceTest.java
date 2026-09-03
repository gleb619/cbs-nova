package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.DslDefinitionLoader;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.LoadResult;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.starter.config.DslReloadRouterConfiguration;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controller.DslReloadHandler;
import cbs.nova.starter.model.PreviewModels;
import cbs.nova.starter.service.PreviewResultCache;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DslReloadResourceTest {

  private DslReloadHandler resource;
  private final DslDefinitionLoader loader = new DefinitionLoader();

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    resource = new DslReloadHandler(new DslProperties(), loader);
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  private void setSourceDir(String value) {
    resource = new DslReloadHandler(dslProperties(value), loader);
  }

  private static ServerRequest reloadRequest() {
    return ServerRequest.create(
            new MockHttpServletRequest("POST", "/api/dsl/reload"), List.of());
  }

  @Test
  void reloadReturns409WhenSourceDirBlank() throws Exception {
    setSourceDir("");
    ServerResponse response = resource.reload(reloadRequest());
    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void reloadReturns409WhenSourceDirNotFound() throws Exception {
    setSourceDir("/tmp/cbs-nova-does-not-exist-" + System.nanoTime());
    ServerResponse response = resource.reload(reloadRequest());
    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void routerFunctionIsRegisteredByDefault() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslPropertiesConfiguration.class,
                    DslReloadRouterConfiguration.class, DslReloadHandler.class)
            .run(ctx -> assertThat(ctx).hasSingleBean(RouterFunction.class));
  }

  @Test
  void routerFunctionSkippedWhenDisabled() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslPropertiesConfiguration.class,
                    DslReloadRouterConfiguration.class, DslReloadHandler.class)
            .withPropertyValues("dsl.reload.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(RouterFunction.class));
  }

  @Test
  void reloadLoadsDefinitionsViaSpiAndNewClassLoader() throws Exception {
    Path sourceDir = createTemporaryDslSourceDir();
    try {
      setSourceDir(sourceDir.toString());
      ServerResponse response = resource.reload(reloadRequest());
      assertThat(response.statusCode().value()).isEqualTo(200);
      assertThat(GlobalManager.globalManager().hasProcess("ReloadTestProcess")).isTrue();
    } finally {
      deleteRecursively(sourceDir);
    }
  }

  /**
   * The reload response body must carry the {@link LoadResult} drilldown: source dir, totals and
   * the per-type counts/names, so operators see WHAT a reload loaded.
   */
  @Test
  void reloadResponseBodyIncludesLoadResultDrilldown() throws Exception {
    Path sourceDir = createTemporaryDslSourceDir();
    try {
      setSourceDir(sourceDir.toString());
      ServerResponse response = resource.reload(reloadRequest());
      assertThat(response.statusCode().value()).isEqualTo(200);

      var node = new ObjectMapper().readTree(renderBody(response));

      assertThat(node.path("sourceDir").asString()).isEqualTo(sourceDir.toString());
      // Drilldown schema: per-type name arrays under "load". The starter test classpath carries
      // its own SPI providers, so the counts are > 1 — what matters is the reloaded source
      // dir's process shows up and the per-type arrays exist.
      assertThat(node.path("load").path("processes").isArray()).isTrue();
      assertThat(node.path("load").path("transactions").isArray()).isTrue();
      assertThat(node.path("load").path("functions").isArray()).isTrue();
      assertThat(toStrings(node.path("load").path("processes")))
              .contains("ReloadTestProcess");
    } finally {
      deleteRecursively(sourceDir);
    }
  }

  /** The programmatic reload path (draft publish) must hand back the same drilldown. */
  @Test
  void reloadDefinitionsReturnsLoadResultDrilldown() throws Exception {
    Path sourceDir = createTemporaryDslSourceDir("PublishTestProcess");
    try {
      setSourceDir(sourceDir.toString());
      LoadResult load = resource.reloadDefinitions();
      assertThat(load.processes()).contains("PublishTestProcess");
      assertThat(load.processCount()).isEqualTo(load.processes().size());
      assertThat(load.transactionCount()).isEqualTo(load.transactions().size());
      assertThat(load.functionCount()).isEqualTo(load.functions().size());
      assertThat(load.total())
              .isEqualTo(load.processCount() + load.transactionCount() + load.functionCount());
      assertThat(load.total()).isPositive();
    } finally {
      deleteRecursively(sourceDir);
    }
  }

  private static List<String> toStrings(JsonNode array) {
    var names = new ArrayList<String>();
    array.forEach(n -> names.add(n.asString()));
    return names;
  }

  private static String renderBody(ServerResponse response) throws Exception {
    var servletResponse = new MockHttpServletResponse();
    response.writeTo(
            new MockHttpServletRequest("POST", "/api/dsl/reload"),
            servletResponse,
            () -> List.of(new JacksonJsonHttpMessageConverter()));
    return servletResponse.getContentAsString();
  }

  /**
   * Failure-safety regression: a compile error during reload must NOT wipe the live registry. The
   * runtime must keep serving definitions that were registered by a previous successful reload.
   */
  @Test
  void failedReloadLeavesExistingRegistryIntact() throws Exception {
    // 1. Initial successful reload: registers ReloadTestProcess.
    Path goodDir = createTemporaryDslSourceDir();
    try {
      setSourceDir(goodDir.toString());
      ServerResponse ok = resource.reload(reloadRequest());
      assertThat(ok.statusCode().value()).isEqualTo(200);
      assertThat(GlobalManager.globalManager().hasProcess("ReloadTestProcess"))
              .as("initial reload must register the process")
              .isTrue();
    } finally {
      deleteRecursively(goodDir);
    }

    // 2. Point at a source dir whose only .java file is broken.
    Path badDir = createTemporaryBrokenDslSourceDir();
    try {
      setSourceDir(badDir.toString());
      ServerResponse fail = resource.reload(reloadRequest());
      assertThat(fail.statusCode().value())
              .as("compile failure must surface as 500")
              .isEqualTo(500);

      // 3. The previous registry must still be live and resolvable.
      assertThat(GlobalManager.globalManager().hasProcess("ReloadTestProcess"))
              .as("previous registry must survive a failed reload")
              .isTrue();
    } finally {
      deleteRecursively(badDir);
    }
  }

  /**
   * The temp working directory created for compilation must be removed on the success path too (it
   * used to be leaked on every reload).
   */
  @Test
  void successfulReloadDeletesTempDir() throws Exception {
    long before = System.currentTimeMillis();
    Path goodDir = createTemporaryDslSourceDir();
    try {
      setSourceDir(goodDir.toString());
      ServerResponse ok = resource.reload(reloadRequest());
      assertThat(ok.statusCode().value()).isEqualTo(200);
      // No leftover dsl-reload- temp directories created during this test.
      assertNoLeakedReloadTempDirs(before);
    } finally {
      deleteRecursively(goodDir);
    }
  }

  /**
   * Same as the success path, but for the failure path: the temp dir must be cleaned up even when
   * the compile throws.
   */
  @Test
  void failedReloadDeletesTempDir() throws Exception {
    long before = System.currentTimeMillis();
    Path badDir = createTemporaryBrokenDslSourceDir();
    try {
      setSourceDir(badDir.toString());
      ServerResponse fail = resource.reload(reloadRequest());
      assertThat(fail.statusCode().value()).isEqualTo(500);
      assertNoLeakedReloadTempDirs(before);
    } finally {
      deleteRecursively(badDir);
    }
  }

  /**
   * After a successful registry swap the preview cache must be flushed end-to-end, so the next
   * preview call cannot return a stale result computed against the previous registry.
   */
  @Test
  void successfulReloadFlushesPreviewCache() throws Exception {
    Path sourceDir = createTemporaryDslSourceDir();
    try {
      var cache = new PreviewResultCache(60_000);
      var key = new PreviewModels.PreviewCacheKey("FlushKey", "old-hash", "input-hash");
      cache.put(key, sampleReport());

      var handler = new DslReloadHandler(
              dslProperties(sourceDir.toString()),
              loader,
              constantProvider(cache));

      ServerResponse response = handler.reload(reloadRequest());
      assertThat(response.statusCode().value()).isEqualTo(200);
      assertThat(cache.get(key))
              .as("preview cache entry must be flushed after successful reload")
              .isNull();
    } finally {
      deleteRecursively(sourceDir);
    }
  }

  /**
   * A failed reload (compile error) must NOT clear the cache: the previous registry is still live,
   * so cached previews computed against it are still valid.
   */
  @Test
  void failedReloadLeavesPreviewCacheIntact() throws Exception {
    Path badDir = createTemporaryBrokenDslSourceDir();
    try {
      var cache = new PreviewResultCache(60_000);
      var key = new PreviewModels.PreviewCacheKey("StaleKey", "old-hash", "input-hash");
      var report = sampleReport();
      cache.put(key, report);

      var handler = new DslReloadHandler(
              dslProperties(badDir.toString()),
              loader,
              constantProvider(cache));

      ServerResponse response = handler.reload(reloadRequest());
      assertThat(response.statusCode().value()).isEqualTo(500);
      assertThat(cache.get(key))
              .as("preview cache must survive a failed reload — registry is still live")
              .isEqualTo(report);
    } finally {
      deleteRecursively(badDir);
    }
  }

  /**
   * The handler must be tolerant of a missing preview cache (e.g. tests that don't wire the bean,
   * or hosts that disable preview caching via configuration). A null provider must not NPE.
   */
  @Test
  void reloadSucceedsWhenPreviewCacheProviderIsNull() throws Exception {
    Path sourceDir = createTemporaryDslSourceDir();
    try {
      var handler = new DslReloadHandler(
              dslProperties(sourceDir.toString()),
              loader,
              null);

      ServerResponse response = handler.reload(reloadRequest());
      assertThat(response.statusCode().value()).isEqualTo(200);
      assertThat(GlobalManager.globalManager().hasProcess("ReloadTestProcess")).isTrue();
    } finally {
      deleteRecursively(sourceDir);
    }
  }

  private static PreviewReport sampleReport() {
    return new PreviewReport(
            "Ping",
            ExecutionMode.PREVIEW,
            true,
            "pong",
            List.of("trace"),
            List.of(),
            Map.of(),
            CallNode.leaf("Ping", CallKind.PROCESS, null, "pong", true),
            List.of(),
            null,
            List.of());
  }

  /**
   * Minimal {@link ObjectProvider} stub for tests: returns the supplied bean from
   * {@link ObjectProvider#getIfAvailable()}, {@code null} otherwise. Avoids pulling in Mockito for
   * three call sites.
   */
  private static <T> ObjectProvider<T> constantProvider(T bean) {
    return new ObjectProvider<>() {
      @Override
      public T getIfAvailable() {
        return bean;
      }

      @Override
      public T getIfUnique() {
        return bean;
      }

      @Override
      public T getObject() {
        throw new UnsupportedOperationException("not used by reload");
      }
    };
  }

  /**
   * Two overlapping reload calls must serialize, not interleave. We arrange for one reload to be
   * in-flight (a gated thread has already entered the loader) and then a second caller starts. The
   * second caller must NOT enter the loader while the first is still in flight — the lock must
   * serialize them. Both must end up with a real response, and the final registry must be the same
   * source dir's contents (no torn state from interleaved partial registrations).
   */
  @Test
  void concurrentReloadsSerialize() throws Exception {
    Path sourceDir = createTemporaryDslSourceDir("ConcurrentProcess");
    try {
      // One shared gating loader (the handler has a single loader field). It tracks the
      // call number so each call can be latched independently. The first call enters and
      // blocks on release[1]; the second call must NOT be able to enter until the first
      // releases, proving the lock is doing its job.
      DslDefinitionLoader gated = new PerCallGatedLoader(loader);

      var sharedHandler = new DslReloadHandler(
              dslProperties(sourceDir.toString()),
              gated);

      ExecutorService pool = Executors.newFixedThreadPool(2);
      try {
        Future<ServerResponse> futureA = pool.submit(() -> sharedHandler.reload(reloadRequest()));
        // Wait for the first call to enter the loader.
        assertThat(PerCallGatedLoader.arrived(1).await(5, TimeUnit.SECONDS))
                .as("first reload should reach its loader")
                .isTrue();
        // Submit the second reload. It must queue on the handler's lock and therefore must
        // NOT enter the loader while the first is still in flight.
        Future<ServerResponse> futureB = pool.submit(() -> sharedHandler.reload(reloadRequest()));

        // Give B time to actually queue on the lock. The loader is still in call #1.
        Thread.sleep(200);
        assertThat(PerCallGatedLoader.callCount())
                .as("second reload must be queued on the lock, not in the loader")
                .isEqualTo(1);

        // Release call #1; it finishes, releases the lock, call #2 enters the loader.
        PerCallGatedLoader.release(1).countDown();
        long t0 = System.currentTimeMillis();
        boolean bArrived = PerCallGatedLoader.arrived(2).await(10, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - t0;
        if (!bArrived) {
          String detail;
          try {
            ServerResponse bStatus = futureB.get(1, TimeUnit.SECONDS);
            detail = "b.status=" + bStatus.statusCode().value();
          } catch (TimeoutException e) {
            detail = "b future timed out: " + e;
          } catch (Exception e) {
            detail = "b future threw: " + e;
          }
          throw new AssertionError(
                  "second reload should reach its loader after first releases the lock, "
                          + "elapsed=" + elapsed + "ms, " + detail);
        }
        assertThat(PerCallGatedLoader.callCount())
                .as("only one loader runs at a time, ever")
                .isEqualTo(2);

        // Release call #2; both reloads will then complete.
        PerCallGatedLoader.release(2).countDown();
        // Now both reloads can finish. Drain them.
        ServerResponse a = futureA.get(10, TimeUnit.SECONDS);
        ServerResponse b = futureB.get(10, TimeUnit.SECONDS);
        assertThat(a.statusCode().value()).isEqualTo(200);
        assertThat(b.statusCode().value()).isEqualTo(200);

        // Final state: the source dir's process must be registered (no torn mix).
        assertThat(GlobalManager.globalManager().hasProcess("ConcurrentProcess"))
                .as("final registry must reflect the source dir (no torn state)")
                .isTrue();
      } finally {
        PerCallGatedLoader.release(1);
        PerCallGatedLoader.release(2);
        PerCallGatedLoader.reset();
        pool.shutdownNow();
        pool.awaitTermination(5, TimeUnit.SECONDS);
      }
    } finally {
      deleteRecursively(sourceDir);
    }
  }

  private static void assertNoLeakedReloadTempDirs(long notBeforeMillis) throws IOException {
    Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
    AtomicReference<Path> leaked = new AtomicReference<>();
    try (Stream<Path> stream = Files.list(tmp)) {
      stream.filter(p -> p.getFileName() != null
              && p.getFileName().toString().startsWith("dsl-reload-"))
              // Only consider temp dirs created during this test. Other test methods or
              // previous test runs may have left their own behind — that's not what this
              // assertion is for. We only care that *this* reload cleaned up.
              .filter(p -> {
                try {
                  return Files.getLastModifiedTime(p).toMillis() >= notBeforeMillis;
                } catch (IOException e) {
                  return false;
                }
              })
              .forEach(leaked::set);
    }
    assertThat(leaked.get())
            .as("no dsl-reload-* temp dir created during this test should remain under %s", tmp)
            .isNull();
  }

  private Path createTemporaryDslSourceDir(String processName) throws IOException {
    Path sourceDir = Files.createTempDirectory("reload-test-source-");
    Path services = sourceDir.resolve("META-INF/services");
    Files.createDirectories(services);
    Files.writeString(services.resolve("cbs.nova.dsl.DslDefinitionProvider"),
            "ReloadTestProvider\n");
    Files.writeString(sourceDir.resolve("ReloadTestProvider.java"), """
            import cbs.nova.dsl.Dsl;
            import cbs.nova.dsl.DslDefinitionProvider;
            import cbs.nova.dsl.DslObject;
            import cbs.nova.dsl.Result;
            import java.util.List;

            public class ReloadTestProvider implements DslDefinitionProvider {
              @Override
              public List<DslObject> definitions() {
                return List.of(
                    Dsl.process("%s").execute(ctx -> Result.success("ok")).build());
              }
            }
            """.formatted(processName));
    return sourceDir;
  }

  private Path createTemporaryBrokenDslSourceDir() throws IOException {
    Path sourceDir = Files.createTempDirectory("reload-test-source-broken-");
    // A .java file that is syntactically invalid — must trip javac and throw.
    Files.writeString(sourceDir.resolve("Broken.java"),
            "this is not valid Java at all; { class Broken { ???");
    return sourceDir;
  }

  private Path createTemporaryDslSourceDir() throws IOException {
    return createTemporaryDslSourceDir("ReloadTestProcess");
  }

  private void deleteRecursively(Path path) throws IOException {
    try (Stream<Path> stream = Files.walk(path)) {
      stream.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
          // ignore
        }
      });
    }
  }

  @Configuration
  @EnableConfigurationProperties(DslProperties.class)
  static class DslPropertiesConfiguration {

    @Bean
    DslDefinitionLoader dslDefinitionLoader() {
      return new DefinitionLoader();
    }
  }

  /**
   * Test-only loader that delegates to a real {@link DslDefinitionLoader} but blocks inside
   * {@code load()} until a per-call release latch is counted down. The test uses this to observe
   * when each call enters the loader and to assert the handler's lock keeps the second caller out
   * of the loader while the first is in flight.
   * <p>
   * State is held in static fields so the test can interact with it from the main thread without
   * needing to pass references around. {@link #reset()} must be called between tests.
   */
  static final class PerCallGatedLoader implements DslDefinitionLoader {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final Map<Integer, CountDownLatch> ARRIVED = new ConcurrentHashMap<>();
    private static final Map<Integer, CountDownLatch> RELEASE = new ConcurrentHashMap<>();

    static CountDownLatch arrived(int callNumber) {
      return ARRIVED.computeIfAbsent(callNumber, k -> new CountDownLatch(1));
    }

    static CountDownLatch release(int callNumber) {
      return RELEASE.computeIfAbsent(callNumber, k -> new CountDownLatch(1));
    }

    static int callCount() {
      return COUNTER.get();
    }

    static void reset() {
      COUNTER.set(0);
      ARRIVED.clear();
      RELEASE.clear();
    }

    private final DslDefinitionLoader delegate;

    PerCallGatedLoader(DslDefinitionLoader delegate) {
      this.delegate = delegate;
    }

    @Override
    public LoadResult load(GlobalManager gm) {
      return load(Thread.currentThread().getContextClassLoader(), gm);
    }

    @Override
    public LoadResult load(Path sourceDir, GlobalManager gm) {
      throw new UnsupportedOperationException("not used by reload");
    }

    @Override
    public LoadResult load(ClassLoader classLoader, GlobalManager gm) {
      int myCall = COUNTER.incrementAndGet();
      arrived(myCall).countDown();
      try {
        if (!release(myCall).await(10, TimeUnit.SECONDS)) {
          throw new AssertionError("release latch " + myCall + " never counted down");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return delegate.load(classLoader, gm);
    }
  }

  private static DslProperties dslProperties(String sourceDir) {
    DslProperties props = new DslProperties();
    props.setSourceDir(sourceDir);
    return props;
  }

}
