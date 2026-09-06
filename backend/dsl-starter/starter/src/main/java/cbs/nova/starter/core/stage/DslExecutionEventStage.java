package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Result;
import cbs.nova.starter.core.event.DslExecutionEvent.DslRunCompletedEvent;
import cbs.nova.starter.core.event.DslExecutionEvent.DslRunStartedEvent;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class DslExecutionEventStage implements DslPipeStage {

  private final DslExecutionEventBus eventBus;

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    eventBus.publish(
            new DslRunStartedEvent(context.getRunId(), context.getName(), context.getMode()));
    try {
      Result<?> result = next.proceed(context);
      eventBus.publish(new DslRunCompletedEvent(
              context.getRunId(), context.getName(), context.getMode(), result));
      return result;
    } catch (RuntimeException ex) {
      eventBus.publish(new DslRunCompletedEvent(
              context.getRunId(), context.getName(), context.getMode(), Result.failure(ex)));
      throw ex;
    }
  }
}
