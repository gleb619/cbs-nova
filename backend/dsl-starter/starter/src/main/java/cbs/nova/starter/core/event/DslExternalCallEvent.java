package cbs.nova.starter.core.event;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record DslExternalCallEvent(
        @NonNull String runId,
        @NonNull String type,
        @NonNull String target,
        @NonNull String operation,
        @Nullable Object payload)
        implements
          DslExecutionEvent {
}
