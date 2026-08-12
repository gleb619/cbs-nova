package cbs.nova.starter.core.event;

import org.jspecify.annotations.NonNull;

public sealed interface DslExecutionEvent
        permits DslRunStartedEvent, DslRunCompletedEvent, DslExternalCallEvent {
}
