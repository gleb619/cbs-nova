package cbs.nova.starter.vhs;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Single event line in a VHS tape file.
 */
public record TapeEvent(
        @JsonProperty("schema_version") @NonNull String schemaVersion,
        @JsonProperty("event_index") int eventIndex,
        @JsonProperty("event_type") @NonNull String eventType,
        @JsonProperty("timestamp") @NonNull String timestamp,
        @JsonProperty("relative_ms") long relativeMs,
        @JsonProperty("call_metadata") @Nullable CallMetadata callMetadata,
        @JsonProperty("input") @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable Object input,
        @JsonProperty("output") @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable Object output,
        @JsonProperty("timing") @NonNull Timing timing,
        @JsonProperty("correlation_id") @Nullable String correlationId,
        @JsonProperty("metadata") @NonNull Map<String, Object> metadata) {

  public record CallMetadata(
          @JsonProperty("call_id") @NonNull String callId,
          @JsonProperty("type") @NonNull String type,
          @JsonProperty("target") @NonNull String target,
          @JsonProperty("operation") @NonNull String operation) {
  }

  public record Timing(
          @JsonProperty("started_at") @Nullable String startedAt,
          @JsonProperty("finished_at") @Nullable String finishedAt,
          @JsonProperty("duration_ms") @Nullable Long durationMs) {
  }
}
