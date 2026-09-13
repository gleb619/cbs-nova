package cbs.nova.starter.helper.model;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code pick} helper.
 *
 * @param source
 *          the source map to project; {@code null} fails, an empty map yields an empty result
 * @param keys
 *          the key allowlist ({@code mode = "pick"}) or denylist ({@code mode = "omit"});
 *          {@code null} or empty fails
 * @param mode
 *          {@code "pick"} (default when {@code null}/blank — keep only listed keys) or
 *          {@code "omit"} (drop listed keys, keep the rest); matched case-insensitively
 */
public record PickIn(
        @Nullable Map<String, Object> source,
        @Nullable List<String> keys,
        @Nullable String mode) {
}
