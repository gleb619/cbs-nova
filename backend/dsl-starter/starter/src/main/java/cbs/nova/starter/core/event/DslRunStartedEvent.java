package cbs.nova.starter.core.event;

import cbs.nova.dsl.ExecutionMode;
import org.jspecify.annotations.NonNull;

public record DslRunStartedEvent(
        @NonNull String runId,
        @NonNull String name,
        @NonNull ExecutionMode mode)
        implements
          DslExecutionEvent {
}
