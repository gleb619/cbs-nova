package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.config.CbsNovaFakesProperties;
import cbs.nova.starter.config.CbsNovaPreviewProperties;
import cbs.nova.starter.core.pipe.ExplainDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunScopedFakeConfig;
import cbs.nova.starter.core.recorder.RunIdKeyedExternalCallRecorder;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import cbs.nova.starter.service.PreviewResultCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

class PreviewTimeoutTest {

  private final ThreadLocalDryRunLoggingContext dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();
  private final RunIdKeyedExternalCallRecorder recorder = new RunIdKeyedExternalCallRecorder(
          dryRunLoggingContext, null);
  private final ContextFactory contextFactory = new ContextFactory();
  private final DryRunLogBufferRegistry bufferRegistry = new DryRunLogBufferRegistry();
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
    var result = pipe.execute("Fast", contextFactory.of("in", ExecutionMode.PREVIEW));
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
    var result = pipe.execute("Slow", contextFactory.of("in", ExecutionMode.PREVIEW));
    long elapsed = System.currentTimeMillis() - start;

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().success()).isFalse();
    assertThat(result.value().errors()).hasSize(1);
    assertThat(result.value().errors().get(0).code()).isEqualTo(PreviewErrorCode.PREVIEW_TIMEOUT);
    assertThat(elapsed).isLessThan(500);
    assertThat(meterRegistry.counter("cbs.nova.preview.timeout.count").count()).isEqualTo(1.0);
  }

  @Test
  void disabledTimeoutRunsInlineAndSlowConstructCompletes() {
    CbsNovaPreviewProperties properties = new CbsNovaPreviewProperties(null, null,
            new CbsNovaPreviewProperties.Execution(0, 4));
    PreviewDslPipe pipe = previewPipe(properties, dispatchExecutor);

    long start = System.currentTimeMillis();
    var result = pipe.execute("Slow", contextFactory.of("in", ExecutionMode.PREVIEW));
    long elapsed = System.currentTimeMillis() - start;

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().success()).isTrue();
    assertThat(result.value().output()).isEqualTo("completed");
    assertThat(elapsed).isGreaterThanOrEqualTo(100);
  }

  @Test
  void timedOutPreviewIsNotStoredInCache() {
    PreviewResultCache cache = new PreviewResultCache(60_000);
    CbsNovaPreviewProperties properties = timeoutProperties(100);
    PreviewDslPipe pipe = new PreviewDslPipe(recorder, contextFactory, dryRunLoggingContext,
            bufferRegistry, DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN, cache,
            properties, new CbsNovaFakesProperties(false, null), new RunScopedFakeConfig(),
            meterRegistry, dispatchExecutor);

    pipe.execute("Slow", contextFactory.of("in", ExecutionMode.PREVIEW));
    pipe.execute("Slow", contextFactory.of("in", ExecutionMode.PREVIEW));

    assertThat(cache.getStats().get("hits")).isEqualTo(0L);
    assertThat(cache.getStats().get("misses")).isEqualTo(2L);
  }

  @Test
  void explainPathHonoursTimeout() {
    ExplainDslPipe pipe = explainPipe(timeoutProperties(100), dispatchExecutor);

    long start = System.currentTimeMillis();
    var result = pipe.execute("Slow", contextFactory.of("in", ExecutionMode.EXPLAIN));
    long elapsed = System.currentTimeMillis() - start;

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().errors())
            .anySatisfy(e -> assertThat(e.code()).isEqualTo(PreviewErrorCode.PREVIEW_TIMEOUT));
    assertThat(elapsed).isLessThan(500);
  }

  private CbsNovaPreviewProperties timeoutProperties(long timeoutMs) {
    return new CbsNovaPreviewProperties(null, null,
            new CbsNovaPreviewProperties.Execution(timeoutMs, 2));
  }

  private PreviewDslPipe previewPipe(CbsNovaPreviewProperties properties,
          ExecutorService executor) {
    return new PreviewDslPipe(recorder, contextFactory, dryRunLoggingContext, bufferRegistry,
            DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN, null, properties,
            new CbsNovaFakesProperties(false, null), new RunScopedFakeConfig(), meterRegistry,
            executor);
  }

  private ExplainDslPipe explainPipe(CbsNovaPreviewProperties properties,
          ExecutorService executor) {
    return new ExplainDslPipe(recorder, contextFactory, dryRunLoggingContext, bufferRegistry,
            DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN, properties,
            new CbsNovaFakesProperties(false, null), new RunScopedFakeConfig(), meterRegistry,
            new ExplainDiagramRenderer(), executor);
  }
}
