package cbs.nova.starter.vhs.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Summary of a recorded VHS tape file discovered by a {@link VhsTapeStore}.
 */
public record TapeSummary(
        @JsonProperty("filename") @NonNull String filename,
        @JsonProperty("recorded_at") @NonNull Instant recordedAt,
        @JsonProperty("run_id") @NonNull String runId,
        @JsonProperty("correlation_id") @Nullable String correlationId,
        @JsonProperty("route") @NonNull String route,
        @JsonProperty("size_bytes") long sizeBytes,
        @JsonProperty("event_count") long eventCount) {
}
