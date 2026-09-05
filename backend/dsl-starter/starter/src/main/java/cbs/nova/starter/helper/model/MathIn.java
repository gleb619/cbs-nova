package cbs.nova.starter.helper.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code math} helper.
 *
 * <p>
 * Only the fields required by the selected {@code mode} are used; the remaining fields may be
 * {@code null}:
 * <ul>
 * <li>{@code "sum"}, {@code "min"}, {@code "max"}, {@code "mean"}, {@code "median"},
 * {@code "stddev"} require {@code numbers}. {@code "percentile"} requires {@code numbers} and
 * {@code p} (a percentile between 0 and 100, inclusive).</li>
 * <li>{@code "clamp"} requires {@code value}, {@code min} and {@code max}.</li>
 * <li>{@code "round"} requires {@code value}; {@code scale} defaults to {@code 0} when null.
 * {@code "abs"} requires {@code value}. {@code "floor"} and {@code "ceil"} require
 * {@code value}.</li>
 * </ul>
 * {@code mode} is matched case-insensitively.
 */
public record MathIn(
        String mode,
        @Nullable List<Number> numbers,
        @Nullable Number value,
        @Nullable Number min,
        @Nullable Number max,
        @Nullable Integer scale,
        @Nullable Double p) {
}
