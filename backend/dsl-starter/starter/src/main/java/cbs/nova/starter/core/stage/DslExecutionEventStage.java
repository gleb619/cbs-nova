package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Result;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.event.DslExecutionEvent.DslRunCompletedEvent;
import cbs.nova.starter.core.event.DslExecutionEvent.DslRunStartedEvent;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.service.CorrelationId;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class DslExecutionEventStage implements DslPipeStage {

  private final DslExecutionEventBus eventBus;

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    String correlationId = CorrelationId.fromMetadata(
            context.dslContext().metadata().get(StarterConstants.CORRELATION_ID_METADATA_KEY));
    eventBus.publish(
            new DslRunStartedEvent(context.runId(), context.name(), context.mode(), correlationId));
    try {
      Result<?> result = next.proceed(context);
      eventBus.publish(new DslRunCompletedEvent(
              context.runId(), context.name(), context.mode(), result, correlationId));
      return result;
    } catch (RuntimeException ex) {
      eventBus.publish(new DslRunCompletedEvent(
              context.runId(), context.name(), context.mode(), Result.failure(ex), correlationId));
      throw ex;
    }
  }
}
