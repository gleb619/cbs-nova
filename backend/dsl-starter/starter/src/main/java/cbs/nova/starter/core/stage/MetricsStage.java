package cbs.nova.starter.core.stage;

import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.core.StarterConstant;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.metric.PreviewMetricsCollector;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.List;

@RequiredArgsConstructor
public final class MetricsStage implements DslPipeStage {

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    if (context.getMode() == ExecutionMode.RUN) {
      return next.proceed(context);
    }
    PreviewMetricsCollector collector = PreviewMetricsCollector.start();
    try {
      return next.proceed(context);
    } finally {
      countCallKinds(context, collector);
      countExternalCalls(context, collector);
      context.setAttribute(StarterConstant.METRICS_ATTRIBUTE, collector.stop());
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
    collector.recordCall(node.kind());
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
      }
    }
  }
}
