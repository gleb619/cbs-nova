package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.listener.ExecutionTraceCollector;
import cbs.nova.dsl.Result;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.pipe.ExplainGraphAccumulators;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * Owns a fresh {@link ExecutionTraceCollector} per run. The collector is created at stage entry,
 * threaded into the DSL {@link Context} so rich contexts append to it, snapshotted into the
 * explain accumulator (or the {@code executionTrace} attribute when no accumulator is present) in
 * a {@code finally} block, and then dropped with the run. One instance == one run, so there is no
 * runId-keyed map to leak.
 */
public final class ExecutionTraceStage implements DslPipeStage {

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    ExecutionTraceCollector collector = new ExecutionTraceCollector();
    Context<?> ctx = context.dslContext().withExecutionTraceCollector(collector);
    DslPipeContext wrappedContext = context.withDslContext(ctx);
    collector.start();
    try {
      return next.proceed(wrappedContext);
    } finally {
      List<String> snapshot = collector.snapshot();
      var accumulator = ExplainGraphAccumulators.resolve(context);
      if (accumulator.isPresent()) {
        accumulator.get().executionTrace(snapshot);
      } else {
        context.setAttribute(StarterConstants.EXECUTION_TRACE_ATTRIBUTE, snapshot);
      }
      collector.stop();
    }
  }
}
