package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.Result;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import org.jspecify.annotations.NonNull;

/**
 * Owns a fresh {@link ExecutionTraceCollector} per run. The collector is created at stage entry,
 * threaded into the DSL {@link Context} so rich contexts append to it, snapshotted into the
 * {@code executionTrace} attribute in a {@code finally} block, and then dropped with the run. One
 * instance == one run, so there is no runId-keyed map to leak.
 */
public final class ExecutionTraceStage implements DslPipeStage {

  @Override
  // TODO: search and move to
  // `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/core/StarterConstant.java` a string
  // constants
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    ExecutionTraceCollector collector = new ExecutionTraceCollector();
    Context<?> ctx = context.getDslContext().withExecutionTraceCollector(collector);
    DslPipeContext wrappedContext = context.withDslContext(ctx);
    collector.start();
    try {
      return next.proceed(wrappedContext);
    } finally {
      context.setAttribute("executionTrace", collector.snapshot());
      collector.stop();
    }
  }
}
