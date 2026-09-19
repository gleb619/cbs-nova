package cbs.nova.starter.vhs.management;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import cbs.nova.starter.vhs.replay.VhsReplayReport;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Response returned by {@code POST /api/v1/vhs/tapes/{runId}/replay}.
 */
public record ReplayRunResponse(
        @JsonProperty("replay_run_id") @NonNull String replayRunId,
        @JsonProperty("status") @NonNull String status,
        @JsonProperty("target") @NonNull String target,
        @JsonProperty("mode") @NonNull String mode,
        @JsonProperty("status_url") @Nullable String statusUrl,
        @JsonProperty("report") @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable VhsReplayReport report) {
}
