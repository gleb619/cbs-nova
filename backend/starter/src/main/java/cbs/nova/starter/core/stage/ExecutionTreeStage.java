package cbs.nova.starter.core.stage;

import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTreeCollector;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class ExecutionTreeStage implements DslPipeStage {

  private final ContextFactory contextFactory;
  private final int maxDepth;

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    if (context.getMode() == ExecutionMode.RUN) {
      return next.proceed(context);
    }
    ExecutionTreeCollector collector = new ExecutionTreeCollector(maxDepth);
    collector.start();
    Context<?> original = context.getDslContext();
    Context<?> modeCtx = contextFactory.of(
            original.body(),
            original.metadata(),
            context.getMode(),
            context.getRunId(),
            original.transactionRouting())
            .withExecutionListener(collector)
            .withExecutionTraceCollector(original.executionTraceCollector());
    DslPipeContext wrappedContext = context.withDslContext(modeCtx);
    try {
      return next.proceed(wrappedContext);
    } finally {
      collector.finish();
      context.setAttribute("astTree", collector.tree().orElse(null));
    }
  }
}
