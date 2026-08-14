package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.Map;

public record PreviewMetricsSnapshot(
        long executionDurationMs,
        long memoryUsedBytes,
        @NonNull Map<CallKind, Integer> callCounts,
        @NonNull Map<String, Integer> externalCallCounts) {

}
