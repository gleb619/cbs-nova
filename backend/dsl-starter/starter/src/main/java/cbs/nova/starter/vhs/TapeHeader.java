package cbs.nova.starter.vhs;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Header line of a VHS tape file. Exactly one header is written as the first line of every tape.
 */
public record TapeHeader(
        @JsonProperty("vhs_tape_format_version") @NonNull String vhsTapeFormatVersion,
        @JsonProperty("schema_version") @NonNull String schemaVersion,
        @JsonProperty("recorded_at") @NonNull String recordedAt,
        @JsonProperty("source_run_id") @NonNull String sourceRunId,
        @JsonProperty("correlation_id") @Nullable String correlationId,
        @JsonProperty("route") @NonNull String route) {
}
