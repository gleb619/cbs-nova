package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code math} helper.
 *
 * <p>
 * The type of {@code result} depends on the selected {@code mode}: aggregation and most scalar
 * transforms ({@code "sum"}, {@code "min"}, {@code "max"}, {@code "mean"}, {@code "median"},
 * {@code "percentile"}, {@code "stddev"}, {@code "clamp"}, {@code "round"}, {@code "abs"}), as well
 * as {@code "floor"}/{@code "ceil"} producing a {@link Long}.
 */
public record MathOut(Object result) {
}
