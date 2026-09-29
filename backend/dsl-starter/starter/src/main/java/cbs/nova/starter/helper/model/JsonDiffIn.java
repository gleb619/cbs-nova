package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code jsonDiff} helper.
 *
 * <p>
 * Both {@code left} and {@code right} are JSON-encoded strings parsed via Jackson; either may be a
 * top-level object or array. A {@code null} or blank input yields an
 * {@link IllegalArgumentException} via {@link cbs.nova.dsl.Result#failure}; malformed JSON yields
 * an {@link IllegalArgumentException} naming the offending side.
 */
public record JsonDiffIn(String left, String right) {
}
