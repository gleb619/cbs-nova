package cbs.nova.starter.core.stage;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import cbs.nova.dsl.Result;
import cbs.nova.starter.core.StarterConstant;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.metric.PreviewMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.List;

@RequiredArgsConstructor
public final class MetricsStage implements DslPipeStage {

  public static final String CALL_COUNTER = "dsl.preview.calls";
  public static final String EXTERNAL_CALL_COUNTER = "dsl.preview.external.calls";
  public static final String DURATION_TIMER = "dsl.preview.duration";

  private final MeterRegistry meterRegistry;

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    if (context.getMode() == ExecutionMode.RUN) {
      return next.proceed(context);
    }
    PreviewMetricsCollector collector = PreviewMetricsCollector.start();
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      return next.proceed(context);
    } finally {
      countCallKinds(context, collector);
      countExternalCalls(context, collector);
      PreviewMetricsSnapshot snapshot = collector.stop();
      sample.stop(Timer.builder(DURATION_TIMER)
              .description("Duration of a preview or explain run")
              .tag("mode", context.getMode().name())
              .tag("process", context.getName())
              .register(meterRegistry));
      context.setAttribute(StarterConstant.METRICS_ATTRIBUTE, snapshot);
    }
  }

  private void countCallKinds(@NonNull DslPipeContext context,
          @NonNull PreviewMetricsCollector collector) {
    CallNode tree = context.getAttribute(StarterConstant.AST_TREE_ATTRIBUTE, CallNode.class);
    if (tree != null) {
      countNode(tree, collector);
    }
  }

  private void countNode(@NonNull CallNode node, @NonNull PreviewMetricsCollector collector) {
    CallKind kind = node.kind();
    collector.recordCall(kind);
    meterRegistry.counter(CALL_COUNTER, "kind", kind.name()).increment();
    for (CallNode child : node.children()) {
      countNode(child, collector);
    }
  }

  @SuppressWarnings("unchecked")
  private void countExternalCalls(@NonNull DslPipeContext context,
          @NonNull PreviewMetricsCollector collector) {
    List<ExternalCall> calls = (List<ExternalCall>) context.getAttribute(
            StarterConstant.EXTERNAL_CALLS_ATTRIBUTE);
    if (calls != null) {
      for (ExternalCall call : calls) {
        collector.recordExternalCall(call.type());
        meterRegistry.counter(EXTERNAL_CALL_COUNTER, "type", call.type()).increment();
      }
    }
  }
}
