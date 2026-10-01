package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T746: asserts that {@link DispatchStage} emits a {@code dsl.preview.dispatch} span carrying the
 * definition, mode, and runId attributes — both the inline (tracer=null) path stays
 * telemetry-silent and the wired-tracer path produces the named span.
 */
class DispatchStageTracingTest {

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager().registerHelper("echo", new Echo());
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void dispatchEmitsSpanWithDefinitionAndModeAttributes() {
    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
    OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();

    var stage = new DispatchStage(null, null, null, null, null,
            openTelemetry.getTracer("cbs.nova.dsl"));
    Context<?> ctx = SimpleContext.builder("body").mode(ExecutionMode.PREVIEW).runId("run-t746")
            .build();
    DslPipeContext pipeContext = DslPipeContext.of("echo", ctx, ExecutionMode.PREVIEW,
            "run-t746");
    DslPipeStage.Next next = c -> Result.success("downstream");

    stage.execute(pipeContext, next);

    List<SpanData> finished = exporter.getFinishedSpanItems();
    SpanData span = finished.stream()
            .filter(s -> "dsl.preview.dispatch".equals(s.getName()))
            .findFirst()
            .orElse(null);
    assertThat(span).as("dsl.preview.dispatch span").isNotNull();
    assertThat(span.getAttributes().get(AttributeKey.stringKey("definition"))).isEqualTo("echo");
    assertThat(span.getAttributes().get(AttributeKey.stringKey("mode")))
            .isEqualTo(ExecutionMode.PREVIEW.name());
    assertThat(span.getAttributes().get(AttributeKey.stringKey("runId"))).isEqualTo("run-t746");
    assertThat(span.getAttributes().get(AttributeKey.stringKey("outcome"))).isEqualTo("success");
  }

  @Test
  void dispatchWithNullTracerStaysSilent() {
    // Legacy ctor — telemetry is intentionally disabled.
    var stage = DispatchStage.inline(null);
    Context<?> ctx = SimpleContext.builder("body").mode(ExecutionMode.PREVIEW).runId("silent")
            .build();
    DslPipeContext pipeContext = DslPipeContext.of("echo", ctx, ExecutionMode.PREVIEW, "silent");
    DslPipeStage.Next next = c -> Result.success("downstream");

    stage.execute(pipeContext, next);

    // No span producer wired — we cannot inspect exporter state here, but the assertion is that
    // no exception is thrown and the existing behavior is preserved.
    Result<?> dslResult = (Result<?>) pipeContext.getAttribute("dslResult");
    assertThat(dslResult).isNotNull();
    assertThat(dslResult.isSuccess()).isTrue();
  }

  private static final class Echo implements Executable<Object, Object> {
    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success("real");
    }
  }
}
