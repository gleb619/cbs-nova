package cbs.nova.starter.core.pipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.fake.FakeConfig;
import cbs.nova.dsl.fake.FakeEntry;
import cbs.nova.dsl.model.ExplainTraceReport;
import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.starter.config.properties.CbsNovaFakesProperties;
import cbs.nova.starter.config.properties.CbsNovaPreviewProperties;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T417: end-to-end assertion that the {@link FakeHelperInterceptor} threaded through PreviewDslPipe
 * / ExplainDslPipe via the {@link Context} (not a ThreadLocal) still fires and short-circuits
 * helpers, and that real (RUN) pipes without configured fakes do not see cross-run state.
 */
class InterceptorThreadingTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ThreadLocalDryRunLoggingContext dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();
  private final DryRunLogBufferRegistry bufferRegistry = new DryRunLogBufferRegistry();
  private final CbsNovaPreviewProperties previewProperties = new CbsNovaPreviewProperties(null,
          null, null);

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager().registerHelper("httpCall",
            (Executable<String, String>) ctx -> Result.success("real-http-result"));
    GlobalManager.globalManager().registerHelper("dbCall",
            (Executable<String, String>) ctx -> Result.success("real-db-result"));
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void previewPipeFiresInterceptorViaContextWithoutThreadLocal() {
    var runScopedFakeConfig = new RunScopedFakeConfig();
    var recorder = mock(ExternalCallRecorder.class);
    runScopedFakeConfig.register("run-preview",
            FakeConfig.of(new FakeEntry("helper", "httpCall", "faked-http")));

    PreviewDslPipe previewPipe = new PreviewDslPipe(recorder, contextFactory,
            dryRunLoggingContext, bufferRegistry,
            DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN, null, previewProperties,
            new CbsNovaFakesProperties(false, null), runScopedFakeConfig,
            new SimpleMeterRegistry(), null);

    Context<?> ctx = contextFactory.of("payload", ExecutionMode.PREVIEW, "run-preview");
    Result<PreviewReport> result = previewPipe.execute("httpCall", ctx);

    assertThat(result.isSuccess()).isTrue();
    verify(recorder).record(eq("helper"), eq("httpCall"), eq("execute"), eq("faked-http"));
  }

  @Test
  void runPipeFiresInterceptorViaContextWithoutThreadLocal() {
    var runScopedFakeConfig = new RunScopedFakeConfig();
    var recorder = mock(ExternalCallRecorder.class);
    runScopedFakeConfig.register("run-fake",
            FakeConfig.of(new FakeEntry("helper", "httpCall", "faked-http")));

    var runPipe = new RunDslPipe(contextFactory, recorder,
            new CbsNovaFakesProperties(false, null), runScopedFakeConfig,
            new DslExecutionEventBus());

    Context<?> ctx = contextFactory.of("payload", ExecutionMode.RUN, "run-fake");
    Result<Object> result = runPipe.execute("httpCall", ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("faked-http");
    verify(recorder).record(eq("helper"), eq("httpCall"), eq("execute"), eq("faked-http"));
  }

  @Test
  void runPipeRunsRealHelperWhenNoFakeConfigured() {
    // T417 regression: pre-T417 a stale ThreadLocal would have leaked fakes into a fresh
    // run; after T417 the interceptor lives on the per-execution Context only.
    var runScopedFakeConfig = new RunScopedFakeConfig();
    var recorder = mock(ExternalCallRecorder.class);

    var runPipe = new RunDslPipe(contextFactory, recorder,
            new CbsNovaFakesProperties(false, null), runScopedFakeConfig,
            new DslExecutionEventBus());

    Context<?> ctx = contextFactory.of("payload", ExecutionMode.RUN, "run-nofake");
    Result<Object> result = runPipe.execute("httpCall", ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("real-http-result");
    // ExternalCallRecordingStage calls startRun/finishRun; what matters here is that the
    // interceptor's record(...) was never invoked (no fake was registered).
    verify(recorder, never()).record(anyString(), anyString(), anyString(), any());
  }

  @Test
  void previewAndRunPipesDoNotShareState() {
    // The PreviewDslPipe registers fakes via the recorder; the RunDslPipe must not inherit
    // those fakes on its first call (no ThreadLocal reuse). This is the leak surface that
    // T417 closes.
    var previewRec = mock(ExternalCallRecorder.class);
    var runRec = mock(ExternalCallRecorder.class);
    var previewScoped = new RunScopedFakeConfig();
    var runScoped = new RunScopedFakeConfig();

    previewScoped.register("run-shared",
            FakeConfig.of(new FakeEntry("helper", "httpCall", "preview-fake")));

    var previewPipe = new PreviewDslPipe(previewRec, contextFactory, dryRunLoggingContext,
            bufferRegistry, DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN, null,
            previewProperties, new CbsNovaFakesProperties(false, null), previewScoped,
            new SimpleMeterRegistry(), null);
    var runPipe = new RunDslPipe(contextFactory, runRec,
            new CbsNovaFakesProperties(false, null), runScoped,
            new DslExecutionEventBus());

    Context<?> previewCtx = contextFactory.of("payload", ExecutionMode.PREVIEW, "run-shared");
    Result<PreviewReport> previewResult = previewPipe.execute("httpCall", previewCtx);
    assertThat(previewResult.value()).isNotNull();
    verify(previewRec).record(eq("helper"), eq("httpCall"), eq("execute"), eq("preview-fake"));

    // The Run pipe never sees previewScoped, so it must run the real helper.
    Context<?> runCtx = contextFactory.of("payload", ExecutionMode.RUN, "run-shared");
    Result<Object> runResult = runPipe.execute("httpCall", runCtx);
    assertThat(runResult.value()).isEqualTo("real-http-result");
    // No fake record(...) on the run recorder: scoped config is separate per pipe.
    verify(runRec, never()).record(anyString(), anyString(), anyString(), any());
  }

  @Test
  void explainPipeThreadsInterceptorViaContextWithoutThreadLocal() {
    var runScopedFakeConfig = new RunScopedFakeConfig();
    var recorder = mock(ExternalCallRecorder.class);
    runScopedFakeConfig.register("run-explain",
            FakeConfig.of(new FakeEntry("helper", "dbCall", "faked-db")));

    var explainPipe = new ExplainDslPipe(recorder, contextFactory, dryRunLoggingContext,
            bufferRegistry, DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN, previewProperties,
            new CbsNovaFakesProperties(false, null), runScopedFakeConfig,
            new SimpleMeterRegistry(), new ExplainDiagramRenderer(), null);

    Context<?> ctx = contextFactory.of("payload", ExecutionMode.EXPLAIN, "run-explain");
    Result<ExplainTraceReport> result = explainPipe.execute("dbCall", ctx);

    assertThat(result.isSuccess()).isTrue();
    verify(recorder).record(eq("helper"), eq("dbCall"), eq("execute"), eq("faked-db"));
  }

  @Test
  void processInvokingHelperThroughContextStillHitsInterceptor() {
    // A process that calls ctx.runHelper(...) must trigger the interceptor when the ctx
    // carries one. T417 threading is end-to-end via Context.
    GlobalManager.globalManager().registerProcess(
            Dsl.process("ProcessCallsHelper")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> ctx.runHelper("httpCall"))
                    .build());

    var runScopedFakeConfig = new RunScopedFakeConfig();
    var recorder = mock(ExternalCallRecorder.class);
    runScopedFakeConfig.register("run-process",
            FakeConfig.of(new FakeEntry("helper", "httpCall", "faked-http")));

    var runPipe = new RunDslPipe(contextFactory, recorder,
            new CbsNovaFakesProperties(false, null), runScopedFakeConfig,
            new DslExecutionEventBus());

    Context<?> ctx = contextFactory.of("payload", ExecutionMode.RUN, "run-process");
    Result<Object> result = runPipe.execute("ProcessCallsHelper", ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("faked-http");
    verify(recorder).record(eq("helper"), eq("httpCall"), eq("execute"), eq("faked-http"));
  }
}
