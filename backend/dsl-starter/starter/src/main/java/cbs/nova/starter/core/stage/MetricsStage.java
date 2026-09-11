package cbs.nova.starter.core.stage;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import cbs.nova.dsl.Result;
import cbs.nova.starter.core.StarterConstants;
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

  private final MeterRegistry meterRegistry;

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    if (context.mode() == ExecutionMode.RUN) {
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
      sample.stop(Timer.builder(StarterConstants.DURATION_TIMER)
              .description("Duration of a preview or explain run")
              .tag("mode", context.mode().name())
              .tag("process", context.name())
              .register(meterRegistry));
      context.setAttribute(StarterConstants.METRICS_ATTRIBUTE, snapshot);
    }
  }

  private void countCallKinds(@NonNull DslPipeContext context,
          @NonNull PreviewMetricsCollector collector) {
    CallNode tree = context.getAttribute(StarterConstants.AST_TREE_ATTRIBUTE, CallNode.class);
    if (tree != null) {
      countNode(tree, collector);
    }
  }

  private void countNode(@NonNull CallNode node, @NonNull PreviewMetricsCollector collector) {
    CallKind kind = node.kind();
    collector.recordCall(kind);
    meterRegistry.counter(StarterConstants.CALL_COUNTER, "kind", kind.name()).increment();
    for (CallNode child : node.children()) {
      countNode(child, collector);
    }
  }

  @SuppressWarnings("unchecked")
  private void countExternalCalls(@NonNull DslPipeContext context,
          @NonNull PreviewMetricsCollector collector) {
    List<ExternalCall> calls = (List<ExternalCall>) context.getAttribute(
            StarterConstants.EXTERNAL_CALLS_ATTRIBUTE);
    if (calls != null) {
      for (ExternalCall call : calls) {
        collector.recordExternalCall(call.type());
        meterRegistry.counter(StarterConstants.EXTERNAL_CALL_COUNTER, "type", call.type())
                .increment();
      }
    }
  }
}
