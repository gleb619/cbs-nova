package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.core.stage.DispatchStage;
import cbs.nova.starter.core.stage.ExecutionTraceStage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class RunDslPipe implements DslExecutionPipe<Object> {

  private final ContextFactory contextFactory;
  private final ExecutionTraceCollector traceCollector;

  @Override
  public @NonNull Result<Object> execute(@NonNull String name,
          @NonNull Context<?> ctx) {
    return DslExecutionPipeline.<Object>builder()
            .stage(new ExecutionTraceStage(traceCollector))
            .stage(new DispatchStage(contextFactory))
            .build()
            .execute(name, ctx);
  }
}
