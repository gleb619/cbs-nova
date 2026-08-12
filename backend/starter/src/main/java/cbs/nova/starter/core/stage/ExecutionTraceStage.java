package cbs.nova.starter.core.stage;

import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.Result;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class ExecutionTraceStage implements DslPipeStage {

  private final ExecutionTraceCollector traceCollector;

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    traceCollector.start(context.getRunId());
    try {
      return next.proceed(context);
    } finally {
      context.setAttribute("executionTrace", traceCollector.snapshot(context.getRunId()));
      traceCollector.stop(context.getRunId());
    }
  }
}
