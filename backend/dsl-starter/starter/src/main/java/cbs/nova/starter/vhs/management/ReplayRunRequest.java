package cbs.nova.starter.vhs.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Body for {@code POST /api/v1/vhs/tapes/{runId}/replay}.
 */
public record ReplayRunRequest(
        @JsonProperty("target") @Nullable String target,
        @JsonProperty("mode") @Nullable String mode,
        @JsonProperty("speed") @Nullable Double speed,
        @JsonProperty("concurrency") @Nullable Integer concurrency,
        @JsonProperty("copies") @Nullable Integer copies) {
}
