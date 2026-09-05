package cbs.nova.starter.helper.model;

import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code semver} helper.
 *
 * <p>
 * Only the fields required by the selected {@code mode} are used; the remaining fields may be
 * {@code null}:
 * <ul>
 * <li>{@code "parse"} requires {@code version}.</li>
 * <li>{@code "compare"} requires {@code versionA} and {@code versionB}.</li>
 * <li>{@code "satisfies"} requires {@code version} and {@code range}.</li>
 * <li>{@code "bump"} requires {@code version} and {@code bumpType}.</li>
 * <li>{@code "format"} requires {@code major}, {@code minor} and {@code patch}; {@code preRelease}
 * and {@code build} are optional.</li>
 * </ul>
 * {@code mode} and {@code bumpType} are matched case-insensitively. A leading {@code "v"} on
 * {@code version}, {@code versionA} or {@code versionB} is stripped before parsing.
 */
public record SemverIn(
        String mode,
        @Nullable String version,
        @Nullable String versionA,
        @Nullable String versionB,
        @Nullable String range,
        @Nullable String bumpType,
        @Nullable Integer major,
        @Nullable Integer minor,
        @Nullable Integer patch,
        @Nullable String preRelease,
        @Nullable String build) {
}
