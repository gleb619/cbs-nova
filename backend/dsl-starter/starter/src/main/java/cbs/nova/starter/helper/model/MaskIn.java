package cbs.nova.starter.helper.model;

import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code mask} helper.
 *
 * <p>
 * {@code mode} selects the masking strategy: {@code null} or {@code "edges"} (case-insensitive)
 * keeps the configured edges and masks the middle; {@code "fixed"} replaces the whole value with
 * {@code width} mask characters. With {@code "edges"} and no {@code keepFirst}/{@code keepLast},
 * the safe default applies. See {@code MaskHelper} for the exact rules.
 */
public record MaskIn(
        @Nullable String value,
        @Nullable String mode,
        @Nullable Integer keepFirst,
        @Nullable Integer keepLast,
        @Nullable String maskChar,
        @Nullable Integer width) {
}
