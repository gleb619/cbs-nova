package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code semver} helper.
 *
 * <p>
 * The runtime type of {@code result} depends on the selected {@code mode}: a
 * {@code Map<String, Object>} with {@code "major"}, {@code "minor"}, {@code "patch"},
 * {@code "preRelease"}, {@code "build"} for {@code "parse"}; an {@link Integer} in {@code {-1, 0,
 * 1}} for {@code "compare"}; a {@link Boolean} for {@code "satisfies"}; and a {@link String} for
 * {@code "bump"} and {@code "format"}.
 */
public record SemverOut(Object result) {
}
