package cbs.nova.starter.helper.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code random} helper.
 *
 * <p>
 * Only the fields required by the selected {@code mode} are used; the remaining fields may be
 * {@code null}:
 * <ul>
 * <li>{@code "int"} requires {@code intMin} and {@code intMax}; bounds are inclusive.</li>
 * <li>{@code "long"} requires {@code longMin} and {@code longMax}; bounds are inclusive.</li>
 * <li>{@code "double"} uses {@code doubleMin}/{@code doubleMax} (defaults {@code 0.0}/{@code 1.0}
 * when null); the upper bound is exclusive.</li>
 * <li>{@code "string"} requires {@code length} (must be in {@code [0, 100000]}); {@code charset}
 * defaults to {@code "alphanumeric"} when null.</li>
 * <li>{@code "choice"} requires {@code list} (must be non-null and non-empty).</li>
 * </ul>
 * {@code mode} is matched case-insensitively.
 */
public record RandomIn(
        String mode,
        @Nullable Integer intMin,
        @Nullable Integer intMax,
        @Nullable Long longMin,
        @Nullable Long longMax,
        @Nullable Double doubleMin,
        @Nullable Double doubleMax,
        @Nullable Integer length,
        @Nullable String charset,
        @Nullable List<Object> list) {
}
