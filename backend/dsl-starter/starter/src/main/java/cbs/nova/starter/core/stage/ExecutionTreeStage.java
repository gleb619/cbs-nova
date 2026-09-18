package cbs.nova.starter.core.stage;

import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.listener.ExecutionTreeCollector;
import cbs.nova.dsl.Result;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.pipe.HierarchyAccumulators;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class ExecutionTreeStage implements DslPipeStage {

  private final int maxDepth;

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    if (context.mode() == ExecutionMode.RUN) {
      return next.proceed(context);
    }
    ExecutionTreeCollector collector = new ExecutionTreeCollector(maxDepth);
    collector.start();
    Context<?> original = context.dslContext();
    Context<?> modeCtx = SimpleContext.builder().body(original.body()).metadata(original.metadata())
            .mode(context.mode()).runId(context.runId())
            .transactionRouting(original.transactionRouting()).build()
            .withExecutionListener(collector)
            .withExecutionTraceCollector(original.executionTraceCollector());
    DslPipeContext wrappedContext = context.withDslContext(modeCtx);
    try {
      return next.proceed(wrappedContext);
    } finally {
      collector.finish();
      var accumulator = HierarchyAccumulators.resolve(context);
      if (accumulator.isPresent()) {
        collector.tree().ifPresent(accumulator.get()::astTree);
      } else {
        context.setAttribute(StarterConstants.AST_TREE_ATTRIBUTE,
                collector.tree().orElse(null));
      }
    }
  }
}
