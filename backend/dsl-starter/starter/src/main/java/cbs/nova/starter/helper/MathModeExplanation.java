package cbs.nova.starter.helper;

import cbs.nova.starter.helper.model.MathIn;
import org.jspecify.annotations.NonNull;

final class MathModeExplanation {

  private MathModeExplanation() {
  }

  static @NonNull String describe(@NonNull String mode, @NonNull MathIn input) {
    return base(mode) + argsDetail(mode, input);
  }

  static @NonNull String diagram(@NonNull String mode) {
    return "graph TD\n  M[math: " + mode + "] --> S[compute " + mode + "]\n  S --> R[MathOut]";
  }

  private static @NonNull String base(@NonNull String mode) {
    return switch (mode) {
      case "sum" ->
        "math helper: computes the double sum of `numbers`. Requires non-empty `numbers`.";
      case "min" ->
        "math helper: returns the numerically smallest value of `numbers`. Requires non-empty `numbers`.";
      case "max" ->
        "math helper: returns the numerically largest value of `numbers`. Requires non-empty `numbers`.";
      case "mean" ->
        "math helper: computes the arithmetic mean of `numbers`. Requires non-empty `numbers`.";
      case "median" -> "math helper: computes the median of `numbers` (average of the two middle"
              + " values for even-length input). Requires non-empty `numbers`.";
      case "percentile" -> "math helper: computes the p-th percentile of `numbers` using linear"
              + " interpolation (Hyndman-Fan type 7). Requires non-empty `numbers` and `p` in [0,100].";
      case "stddev" -> "math helper: computes the sample standard deviation of `numbers`"
              + " (Bessel-corrected). Requires at least two numbers.";
      case "clamp" -> "math helper: clamps `value` into the inclusive [`min`, `max`] range."
              + " Requires `value`, `min`, and `max`.";
      case "round" -> "math helper: rounds `value` to `scale` decimal places (HALF_UP via"
              + " BigDecimal, default scale 0). Requires `value`.";
      case "abs" -> "math helper: returns the absolute value of `value`. Requires `value`.";
      case "floor" ->
        "math helper: returns the largest `long` not exceeding `value`. Requires `value`.";
      case "ceil" -> "math helper: returns the smallest `long` greater than or equal to `value`."
              + " Requires `value`.";
      default -> "math helper: unknown mode `" + mode + "`; expected one of sum, min, max, mean,"
              + " median, percentile, stddev, clamp, round, abs, floor, ceil.";
    };
  }

  private static @NonNull String argsDetail(@NonNull String mode, @NonNull MathIn input) {
    return switch (mode) {
      case "percentile" -> input.p() != null ? " Args: p=" + input.p() + "." : "";
      case "round" -> input.scale() != null ? " Args: scale=" + input.scale() + "." : "";
      case "clamp" -> " Args: min=" + input.min() + ", max=" + input.max() + ".";
      default -> "";
    };
  }
}
