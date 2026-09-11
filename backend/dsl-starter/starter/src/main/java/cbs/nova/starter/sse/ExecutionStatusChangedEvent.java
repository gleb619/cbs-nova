package cbs.nova.starter.sse;

import java.time.Instant;
import org.jspecify.annotations.NonNull;

public record ExecutionStatusChangedEvent(
        @NonNull String id,
        @NonNull String status,
        @NonNull Instant timestamp) {
}
