package cbs.nova.starter.core.event;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public sealed interface DslExecutionEvent
        permits DslExecutionEvent.DslRunStartedEvent, DslExecutionEvent.DslRunCompletedEvent,
        DslExecutionEvent.DslExternalCallEvent {

  record DslExternalCallEvent(
          @Nullable String runId,
          @NonNull String type,
          @NonNull String target,
          @NonNull String operation,
          @Nullable Object payload)
          implements
            DslExecutionEvent {
  }

  record DslRunCompletedEvent(
          @NonNull String runId,
          @NonNull String name,
          @NonNull ExecutionMode mode,
          @Nullable Result<?> result,
          @Nullable String correlationId)
          implements
            DslExecutionEvent {
  }

  record DslRunStartedEvent(
          @NonNull String runId,
          @NonNull String name,
          @NonNull ExecutionMode mode,
          @Nullable String correlationId)
          implements
            DslExecutionEvent {
  }
}
