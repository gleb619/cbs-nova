package cbs.nova.dsl.history;

import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Builder
public record DslRun(
        @NonNull String runId,
        @NonNull String processName,
        @NonNull String status,
        @Nullable String input,
        @Nullable String output,
        @Nullable String error,
        @Nullable String contextJson,
        @NonNull Instant startedAt,
        @Nullable Instant finishedAt,
        @Nullable String executionMode,
        @Nullable String triggeredBy,
        @Nullable String correlationId) {
}
