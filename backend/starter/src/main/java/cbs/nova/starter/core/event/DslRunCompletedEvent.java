package cbs.nova.starter.core.event;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record DslRunCompletedEvent(
        @NonNull String runId,
        @NonNull String name,
        @NonNull ExecutionMode mode,
        @Nullable Result<?> result)
        implements
          DslExecutionEvent {
}
