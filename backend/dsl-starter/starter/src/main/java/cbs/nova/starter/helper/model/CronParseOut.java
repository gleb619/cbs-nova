package cbs.nova.starter.helper.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Output for the built-in {@code cronParse} helper.
 *
 * <p>
 * {@code valid} is {@code true} when the expression is valid and the requested operation succeeded.
 * {@code timestamps} contains up to {@code count} ISO-8601 fire-time strings for {@code nextFire};
 * it is {@code null} for {@code validate}. {@code error} is reserved for structured error payloads
 * and is currently always {@code null} (failures are surfaced via
 * {@link cbs.nova.dsl.Result#failure(Throwable)}).
 */
public record CronParseOut(
        boolean valid,
        @Nullable List<String> timestamps,
        @Nullable String error) {
}
