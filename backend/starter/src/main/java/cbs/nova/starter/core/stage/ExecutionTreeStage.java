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
    collector.startRun(context.getRunId());
    Context<?> modeCtx = contextFactory.of(
        context.getDslContext().body(),
        context.getDslContext().metadata(),
        context.getMode(),
        context.getRunId(),
        context.getDslContext().transactionRouting())
        .withExecutionListener(collector);
    DslPipeContext wrappedContext = context.withDslContext(modeCtx);
    try {
      return next.proceed(wrappedContext);
    } finally {
      collector.finishRun(context.getRunId());
      context.setAttribute("astTree", collector.tree(context.getRunId()).orElse(null));
    }
  }
}
