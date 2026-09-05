package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.MathIn;
import cbs.nova.starter.helper.model.MathOut;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.NonNull;

/**
 * Performs common numeric aggregations and scalar transforms.
 *
 * <p>
 * The helper supports twelve modes (case-insensitive):
 * <ul>
 * <li>{@code "sum"}: double sum of {@code numbers}.</li>
 * <li>{@code "min"}: numerically smallest value of {@code numbers}.</li>
 * <li>{@code "max"}: numerically largest value of {@code numbers}.</li>
 * <li>{@code "mean"}: arithmetic average of {@code numbers}.</li>
 * <li>{@code "median"}: middle value of the sorted {@code numbers} (average of the two middle
 * values for even-length input).</li>
 * <li>{@code "percentile"}: {@code p}-th percentile of {@code numbers} using linear interpolation
 * between adjacent sorted values (NumPy {@code "linear"} / Hyndman-Fan type 7). {@code p} must be
 * between 0 and 100 inclusive.</li>
 * <li>{@code "stddev"}: sample standard deviation (Bessel-corrected, divided by {@code N-1}); at
 * least two elements are required.</li>
 * <li>{@code "clamp"}: clamps {@code value} into the inclusive [{@code min}, {@code max}]
 * range.</li>
 * <li>{@code "round"}: rounds {@code value} to {@code scale} decimal places with
 * {@link RoundingMode#HALF_UP} semantics via {@link BigDecimal}.</li>
 * <li>{@code "abs"}: absolute value of {@code value}.</li>
 * <li>{@code "floor"}: largest {@code long} value not exceeding {@code value}.</li>
 * <li>{@code "ceil"}: smallest {@code long} value greater than or equal to {@code value}.</li>
 * </ul>
 *
 * <p>
 * Null/empty {@code numbers} for aggregations, a non-numeric element anywhere in {@code numbers}
 * (reported with its index), missing required scalar arguments, or an unknown {@code mode} all
 * yield an {@link IllegalArgumentException}.
 */
@Helper(name = "math")
public class MathHelper implements Executable<MathIn, MathOut> {

  @Override
  public @NonNull Result<MathOut> execute(@NonNull Context<MathIn> ctx) {
    try {
      MathIn input = ctx.body();
      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "sum" -> sum(input.numbers());
        case "min" -> min(input.numbers());
        case "max" -> max(input.numbers());
        case "mean" -> mean(input.numbers());
        case "median" -> median(input.numbers());
        case "percentile" -> percentile(input.numbers(), input.p());
        case "stddev" -> stddev(input.numbers());
        case "clamp" -> clamp(input.value(), input.min(), input.max());
        case "round" -> round(input.value(), input.scale());
        case "abs" -> abs(input.value());
        case "floor" -> floor(input.value());
        case "ceil" -> ceil(input.value());
        case null, default -> Result.failure(
                new IllegalArgumentException(
                        "math.mode must be one of sum, min, max, mean, median, percentile,"
                                + " stddev, clamp, round, abs, floor, ceil, was: "
                                + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static @NonNull Result<MathOut> sum(List<Number> numbers) {
    double total = 0.0;
    for (double value : requireNumbers(numbers, "sum")) {
      total += value;
    }
    return Result.success(new MathOut(total));
  }

  private static @NonNull Result<MathOut> min(List<Number> numbers) {
    double[] values = requireNumbers(numbers, "min");
    double result = values[0];
    for (int i = 1; i < values.length; i++) {
      result = Math.min(result, values[i]);
    }
    return Result.success(new MathOut(result));
  }

  private static @NonNull Result<MathOut> max(List<Number> numbers) {
    double[] values = requireNumbers(numbers, "max");
    double result = values[0];
    for (int i = 1; i < values.length; i++) {
      result = Math.max(result, values[i]);
    }
    return Result.success(new MathOut(result));
  }

  private static @NonNull Result<MathOut> mean(List<Number> numbers) {
    double[] values = requireNumbers(numbers, "mean");
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return Result.success(new MathOut(total / values.length));
  }

  private static @NonNull Result<MathOut> median(List<Number> numbers) {
    double[] values = requireNumbers(numbers, "median");
    Arrays.sort(values);
    double result;
    if (values.length % 2 == 1) {
      result = values[values.length / 2];
    } else {
      int mid = values.length / 2;
      result = (values[mid - 1] + values[mid]) / 2.0;
    }
    return Result.success(new MathOut(result));
  }

  private static @NonNull Result<MathOut> percentile(List<Number> numbers, Double p) {
    if (p == null) {
      throw new IllegalArgumentException("math.percentile: p is required");
    }
    if (p < 0 || p > 100) {
      throw new IllegalArgumentException("math.percentile: p must be between 0 and 100");
    }
    double[] values = requireNumbers(numbers, "percentile");
    Arrays.sort(values);
    double index = (p / 100.0) * (values.length - 1);
    int lower = (int) Math.floor(index);
    int upper = (int) Math.ceil(index);
    double result;
    if (lower == upper) {
      result = values[lower];
    } else {
      result = values[lower] + (values[upper] - values[lower]) * (index - lower);
    }
    return Result.success(new MathOut(result));
  }

  private static @NonNull Result<MathOut> stddev(List<Number> numbers) {
    double[] values = requireNumbers(numbers, "stddev");
    if (values.length < 2) {
      throw new IllegalArgumentException("math.stddev: requires at least two numbers");
    }
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    double mean = total / values.length;
    double sumOfSquares = 0.0;
    for (double value : values) {
      double deviation = value - mean;
      sumOfSquares += deviation * deviation;
    }
    return Result.success(new MathOut(Math.sqrt(sumOfSquares / (values.length - 1))));
  }
  private static @NonNull Result<MathOut> clamp(Number value, Number min, Number max) {
    if (value == null) {
      throw new IllegalArgumentException("math.clamp: value is required");
    }
    if (min == null) {
      throw new IllegalArgumentException("math.clamp: min is required");
    }
    if (max == null) {
      throw new IllegalArgumentException("math.clamp: max is required");
    }
    double v = value.doubleValue();
    double lower = min.doubleValue();
    double upper = max.doubleValue();
    if (lower > upper) {
      throw new IllegalArgumentException("math.clamp: min cannot exceed max");
    }
    double result = Math.max(lower, Math.min(upper, v));
    return Result.success(new MathOut(result));
  }

  private static @NonNull Result<MathOut> round(Number value, Integer scale) {
    if (value == null) {
      throw new IllegalArgumentException("math.round: value is required");
    }
    int effectiveScale = (scale == null) ? 0 : scale;
    if (effectiveScale < -1 || effectiveScale > 15) {
      throw new IllegalArgumentException("math.round: scale must be between -1 and 15");
    }
    double result = BigDecimal.valueOf(value.doubleValue())
            .setScale(effectiveScale, RoundingMode.HALF_UP)
            .doubleValue();
    return Result.success(new MathOut(result));
  }

  private static @NonNull Result<MathOut> abs(Number value) {
    if (value == null) {
      throw new IllegalArgumentException("math.abs: value is required");
    }
    return Result.success(new MathOut(Math.abs(value.doubleValue())));
  }

  private static @NonNull Result<MathOut> floor(Number value) {
    if (value == null) {
      throw new IllegalArgumentException("math.floor: value is required");
    }
    return Result.success(new MathOut((long) Math.floor(value.doubleValue())));
  }

  private static @NonNull Result<MathOut> ceil(Number value) {
    if (value == null) {
      throw new IllegalArgumentException("math.ceil: value is required");
    }
    return Result.success(new MathOut((long) Math.ceil(value.doubleValue())));
  }
  /**
   * Validates and converts {@code numbers} into a {@code double[]}. Null or empty input, and any
   * non-numeric element (reported by its index), yield an {@link IllegalArgumentException}.
   */
  private static double[] requireNumbers(List<Number> numbers, String op) {
    if (numbers == null || numbers.isEmpty()) {
      throw new IllegalArgumentException("math." + op + ": numbers is required");
    }
    List<?> raw = numbers;
    double[] values = new double[raw.size()];
    for (int i = 0; i < raw.size(); i++) {
      Object element = raw.get(i);
      if (!(element instanceof Number number)) {
        throw new IllegalArgumentException(
                "math." + op + ": non-numeric element at index " + i + ": " + preview(element));
      }
      values[i] = number.doubleValue();
    }
    return values;
  }

  private static String preview(Object value) {
    if (value == null) {
      return "null";
    }
    String string = String.valueOf(value);
    return string.length() > 40 ? string.substring(0, 40) + "..." : string;
  }
}
