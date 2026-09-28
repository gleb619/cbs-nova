package cbs.nova.starter.helper.model;

import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code cronParse} helper.
 *
 * <p>
 * {@code expression} is the cron string to validate or evaluate. {@code mode} selects the
 * operation: {@code "validate"} (default) checks expression syntax, and {@code "nextFire"} computes
 * upcoming fire times. For {@code nextFire}, {@code after} is an optional ISO-8601 timestamp
 * (default {@code Instant.now()}) and {@code count} is the number of fire times to compute (default
 * {@code 1}).
 */
public record CronParseIn(
        @Nullable String expression,
        @Nullable String mode,
        @Nullable String after,
        @Nullable Integer count) {
}
