package cbs.nova.starter.vhs.loadtest;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Body for {@code POST /api/v1/vhs/loadtest}.
 */
public record VhsLoadTestRequest(
        @JsonProperty("tapes") @Nullable String tapes,
        @JsonProperty("target") @Nullable String target,
        @JsonProperty("speed") @Nullable Double speed,
        @JsonProperty("concurrency") @Nullable Integer concurrency,
        @JsonProperty("duration") @Nullable Long duration) {
}
