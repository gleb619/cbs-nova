package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.service.PreviewResultCache;
import cbs.nova.starter.config.CbsNovaFakesProperties;
import cbs.nova.starter.config.CbsNovaPreviewProperties;
import cbs.nova.starter.core.pipe.ExplainDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunDslPipe;
import cbs.nova.starter.core.pipe.RunScopedFakeConfig;
import cbs.nova.starter.core.recorder.RunIdKeyedExternalCallRecorder;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class DevDslRuntimeCachingTest {
  private final ThreadLocalDryRunLoggingContext dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();

  private final RunIdKeyedExternalCallRecorder recorder = new RunIdKeyedExternalCallRecorder(
          dryRunLoggingContext, null);
  private final ContextFactory contextFactory = new ContextFactory();
  private final DryRunLogBufferRegistry bufferRegistry = new DryRunLogBufferRegistry();
  private final DryRunLogbackAppender appender = new DryRunLogbackAppender(dryRunLoggingContext,
          bufferRegistry);
  private Appender<ILoggingEvent> originalDryRunAppender;
  private PreviewResultCache cache;
  private DevDslRuntime runtime;
  private final AtomicInteger executions = new AtomicInteger();

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    executions.set(0);
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("Ping")
                    .execute(ctx -> Result.success("pong-" + executions.incrementAndGet()))
                    .build());

    cache = new PreviewResultCache(60_000);
    CbsNovaPreviewProperties previewProperties = new CbsNovaPreviewProperties(null, null);
    PreviewDslPipe previewPipe = new PreviewDslPipe(recorder, contextFactory,
            dryRunLoggingContext, bufferRegistry, DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN,
            cache, previewProperties, new CbsNovaFakesProperties(false, null),
            new RunScopedFakeConfig(), new SimpleMeterRegistry());
    RunDslPipe runPipe = new RunDslPipe(contextFactory, recorder,
            new CbsNovaFakesProperties(false, null), new RunScopedFakeConfig());
    ExplainDslPipe explainPipe = new ExplainDslPipe(recorder, contextFactory,
            dryRunLoggingContext, bufferRegistry, DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN,
            previewProperties, new CbsNovaFakesProperties(false, null), new RunScopedFakeConfig(),
            new SimpleMeterRegistry(), new ExplainDiagramRenderer());
    runtime = new DevDslRuntime(previewPipe, runPipe, explainPipe);

    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    originalDryRunAppender = root.getAppender("DRY_RUN");
    if (originalDryRunAppender != null) {
      root.detachAppender(originalDryRunAppender);
    }
    appender.setContext(root.getLoggerContext());
    appender.setName("DRY_RUN");
    appender.start();
    root.addAppender(appender);
    root.setLevel(Level.INFO);
  }

  @AfterEach
  void tearDown() {
    dryRunLoggingContext.clearRunId();
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.detachAppender(appender);
    appender.stop();
    if (originalDryRunAppender != null) {
      root.addAppender(originalDryRunAppender);
    }
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void secondPreviewWithSameInputReturnsCachedResult() {
    var ctx = contextFactory.of("input", ExecutionMode.PREVIEW);

    var first = runtime.preview("Ping", ctx);
    var second = runtime.preview("Ping", ctx);

    assertThat(first.isSuccess()).isTrue();
    assertThat(second.isSuccess()).isTrue();
    assertThat(first.value().output()).isEqualTo("pong-1");
    assertThat(second.value().output()).isEqualTo("pong-1");
    assertThat(executions).hasValue(1);
    assertThat(cache.getStats().get("hits")).isEqualTo(1L);
    assertThat(cache.getStats().get("misses")).isEqualTo(1L);
  }

  @Test
  void changingDslDescriptorHashInvalidatesCache() {
    var ctx = contextFactory.of("input", ExecutionMode.PREVIEW);

    var first = runtime.preview("Ping", ctx);
    assertThat(first.value().output()).isEqualTo("pong-1");
    assertThat(cache.getStats().get("misses")).isEqualTo(1L);

    GlobalManager.globalManager().registerProcess(Dsl.process("Ping")
            .version("v2")
            .describe(() -> new DslDescriptor(
                    "Ping",
                    DslType.PROCESS,
                    "changed",
                    String.class,
                    String.class,
                    false,
                    true,
                    "delegates to execute",
                    List.of(ParameterDescriptor.ofString("x")),
                    "Ping-queue",
                    "v2",
                    null,
                    null))
            .execute(ctx2 -> Result.success("pong-" + executions.incrementAndGet())).build());

    var second = runtime.preview("Ping", ctx);
    assertThat(second.value().output()).isEqualTo("pong-2");
    assertThat(cache.getStats().get("misses")).isEqualTo(2L);
  }
}
