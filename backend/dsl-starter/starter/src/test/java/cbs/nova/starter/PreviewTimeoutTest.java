package cbs.nova.starter;

import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.cache.PreviewResultCacheTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.HierarchyReport;
import cbs.nova.starter.config.properties.CbsNovaFakesProperties;
import cbs.nova.starter.config.properties.CbsNovaPreviewProperties;
import cbs.nova.starter.config.properties.DryRunProperties;
import cbs.nova.starter.core.pipe.HierarchyDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunScopedFakeConfig;
import cbs.nova.starter.core.recorder.RunIdKeyedExternalCallRecorder;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import com.github.benmanes.caffeine.cache.Caffeine;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import cbs.nova.starter.reporting.HierarchyDiagramRenderer;
import cbs.nova.starter.service.PreviewResultCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

class PreviewTimeoutTest {

  private static int defaultMaxEventsPerRun() {
    return new DryRunProperties(null, null).log().maxEventsPerRun();
  }

  private final ThreadLocalDryRunLoggingContext dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();
  private final RunIdKeyedExternalCallRecorder recorder = new RunIdKeyedExternalCallRecorder(
          dryRunLoggingContext, null);
  private final DryRunLogBufferRegistry bufferRegistry = new DryRunLogBufferRegistry(
          Caffeine.newBuilder().build());
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private ExecutorService dispatchExecutor;

  @BeforeEach
  void setUp() {
    dispatchExecutor = Executors.newFixedThreadPool(2);
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager().registerProcess(Dsl.process("Fast")
            .execute(ctx -> Result.success("ok"))
            .build());
    GlobalManager.globalManager().registerProcess(Dsl.process("Slow")
            .execute(ctx -> {
              try {
                Thread.sleep(5_000);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return Result.success("completed");
            })
            .explain(ctx -> {
              try {
                Thread.sleep(5_000);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return Result.success(new ExplainReport("Slow", "slow explanation", "", List.of()));
            })
            .build());
  }

  @AfterEach
  void tearDown() {
    if (dispatchExecutor != null) {
      dispatchExecutor.shutdownNow();
    }
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void fastConstructSucceedsWhenTimeoutIsConfigured() {
    PreviewDslPipe pipe = previewPipe(timeoutProperties(200), dispatchExecutor);

    long start = System.currentTimeMillis();
    var result = pipe.execute("Fast",
            SimpleContext.builder("in").mode(ExecutionMode.PREVIEW).build());
    long elapsed = System.currentTimeMillis() - start;

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().success()).isTrue();
    assertThat(result.value().output()).isEqualTo("ok");
    assertThat(elapsed).isLessThan(500);
  }

  @Test
  void slowConstructFailsWithPreviewTimeoutWithinTimeoutAndSmallMargin() {
    PreviewDslPipe pipe = previewPipe(timeoutProperties(100), dispatchExecutor);

    long start = System.currentTimeMillis();
    var result = pipe.execute("Slow",
            SimpleContext.builder("in").mode(ExecutionMode.PREVIEW).build());
    long elapsed = System.currentTimeMillis() - start;

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().success()).isFalse();
    assertThat(result.value().errors()).hasSize(1);
    assertThat(result.value().errors().get(0).code())
            .isEqualTo(PreviewErrorCode.PREVIEW_TIMEOUT.name());
    assertThat(elapsed).isLessThan(500);
    assertThat(meterRegistry.counter("dsl.preview.timeout").count()).isEqualTo(1.0);
  }

  @Test
  void disabledTimeoutRunsInlineAndSlowConstructCompletes() {
    CbsNovaPreviewProperties properties = new CbsNovaPreviewProperties(null, null,
            new CbsNovaPreviewProperties.Execution(0, 4));
    PreviewDslPipe pipe = previewPipe(properties, dispatchExecutor);

    long start = System.currentTimeMillis();
    var result = pipe.execute("Slow",
            SimpleContext.builder("in").mode(ExecutionMode.PREVIEW).build());
    long elapsed = System.currentTimeMillis() - start;

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().success()).isTrue();
    assertThat(result.value().output()).isEqualTo("completed");
    assertThat(elapsed).isGreaterThanOrEqualTo(100);
  }

  @Test
  void timedOutPreviewIsNotStoredInCache() {
    PreviewResultCache cache = PreviewResultCacheTestSupport.cache(60_000);
    CbsNovaPreviewProperties properties = timeoutProperties(100);
    PreviewDslPipe pipe = new PreviewDslPipe(recorder, dryRunLoggingContext,
            bufferRegistry, defaultMaxEventsPerRun(), cache,
            properties, new CbsNovaFakesProperties(false, null),
            new RunScopedFakeConfig(Caffeine.newBuilder().build()),
            meterRegistry, dispatchExecutor);

    pipe.execute("Slow", SimpleContext.builder("in").mode(ExecutionMode.PREVIEW).build());
    pipe.execute("Slow", SimpleContext.builder("in").mode(ExecutionMode.PREVIEW).build());

    assertThat(cache.getStats().get("hits")).isEqualTo(0L);
    assertThat(cache.getStats().get("misses")).isEqualTo(2L);
  }

  @Test
  void explainPathHonoursTimeout() {
    HierarchyDslPipe pipe = hierarchyPipe(timeoutProperties(100), dispatchExecutor);

    long start = System.currentTimeMillis();
    var result = pipe.execute("Slow",
            SimpleContext.builder("in").mode(ExecutionMode.HIERARCHY).build());
    long elapsed = System.currentTimeMillis() - start;

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().errors())
            .anySatisfy(
                    e -> assertThat(e.code()).isEqualTo(PreviewErrorCode.PREVIEW_TIMEOUT.name()));
    assertThat(elapsed).isLessThan(500);
  }

  private CbsNovaPreviewProperties timeoutProperties(long timeoutMs) {
    return new CbsNovaPreviewProperties(null, null,
            new CbsNovaPreviewProperties.Execution(timeoutMs, 2));
  }

  private PreviewDslPipe previewPipe(CbsNovaPreviewProperties properties,
          ExecutorService executor) {
    return new PreviewDslPipe(recorder, dryRunLoggingContext, bufferRegistry,
            defaultMaxEventsPerRun(), null, properties,
            new CbsNovaFakesProperties(false, null),
            new RunScopedFakeConfig(Caffeine.newBuilder().build()), meterRegistry,
            executor);
  }

  private HierarchyDslPipe hierarchyPipe(CbsNovaPreviewProperties properties,
          ExecutorService executor) {
    return new HierarchyDslPipe(recorder, dryRunLoggingContext, bufferRegistry,
            defaultMaxEventsPerRun(), properties,
            new CbsNovaFakesProperties(false, null),
            new RunScopedFakeConfig(Caffeine.newBuilder().build()), meterRegistry,
            new HierarchyDiagramRenderer(), executor);
  }
}
