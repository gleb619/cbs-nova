package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.starter.helper.model.ArithmeticIn;
import cbs.nova.starter.helper.model.ArithmeticIn.Operation;
import cbs.nova.starter.helper.model.ArithmeticOut;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.NonNull;

@Helper(name = "arithmetic")
public class ArithmeticHelper implements Executable<ArithmeticIn, ArithmeticOut> {

  @Override
  public @NonNull Result<ArithmeticOut> execute(@NonNull Context<ArithmeticIn> ctx) {
    try {
      ArithmeticIn input = ctx.body();
      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      Operation operation = input.effectiveOperation();
      if (mode != null) {
        return mathMode(mode, input);
      }
      if (operation != null) {
        return arithmeticOperation(operation, input);
      }
      return arithmeticOperation(Operation.ADD, input);
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  @Override
  public @NonNull ExplainReport explain(@NonNull Context<ArithmeticIn> ctx) {
    ArithmeticIn input = ctx.body();
    String mode = (input.mode() == null) ? "unknown" : input.mode().toLowerCase(Locale.ROOT);
    String description = describe(mode, input);
    String mermaid = """
        graph TD
          A[arithmetic: %s] --> S[compute %s]
          S --> R[ArithmeticOut]""".formatted(mode, mode);
    return new ExplainReport("arithmetic", description, mermaid, List.of());
  }

  private @NonNull Result<ArithmeticOut> mathMode(
          @NonNull String mode,
          @NonNull ArithmeticIn input) {
    return switch (mode) {
      case "sum" -> sum(input.effectiveNumbers());
      case "min" -> min(input.effectiveNumbers());
      case "max" -> max(input.effectiveNumbers());
      case "mean" -> mean(input.effectiveNumbers());
      case "median" -> median(input.effectiveNumbers());
      case "percentile" -> percentile(input.effectiveNumbers(), input.p());
      case "stddev" -> stddev(input.effectiveNumbers());
      case "clamp" -> clamp(input.value(), input.min(), input.max());
      case "round" -> round(input.value(), input.scale());
      case "abs" -> abs(input.value());
      case "floor" -> floor(input.value());
      case "ceil" -> ceil(input.value());
      case null, default -> Result.failure(new IllegalArgumentException(
              "arithmetic.mode must be one of sum, min, max, mean, median, percentile, stddev, clamp, round, abs, floor, ceil, was: "
                      + input.mode()));
    };
  }

  private @NonNull Result<ArithmeticOut> arithmeticOperation(
          @NonNull Operation operation,
          @NonNull ArithmeticIn input) {
    List<Number> values = input.effectiveNumbers();
    if (values == null || values.isEmpty()) {
      return Result.success(new ArithmeticOut(BigDecimal.ZERO));
    }

    BigDecimal result = switch (operation) {
      case ADD -> values.stream()
              .map(this::toBigDecimal)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      case SUBTRACT -> subtract(values);
      case MULTIPLY -> values.stream()
              .map(this::toBigDecimal)
              .reduce(BigDecimal.ONE, BigDecimal::multiply);
      case DIVIDE -> divide(values);
      case MIN -> values.stream()
              .map(this::toBigDecimal)
              .reduce(BigDecimal::min)
              .orElse(BigDecimal.ZERO);
      case MAX -> values.stream()
              .map(this::toBigDecimal)
              .reduce(BigDecimal::max)
              .orElse(BigDecimal.ZERO);
    };

    return Result.success(new ArithmeticOut(result));
  }

  private BigDecimal subtract(List<Number> values) {
    BigDecimal first = toBigDecimal(values.get(0));
    return values.stream()
            .skip(1)
            .map(this::toBigDecimal)
            .reduce(first, BigDecimal::subtract);
  }

  private BigDecimal divide(List<Number> values) {
    BigDecimal first = toBigDecimal(values.get(0));
    return values.stream()
            .skip(1)
            .map(this::toBigDecimal)
            .reduce(first, (a, b) -> a.divide(b, MathContext.DECIMAL64));
  }

  private BigDecimal toBigDecimal(Number value) {
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    return BigDecimal.valueOf(value.doubleValue());
  }

  private @NonNull Result<ArithmeticOut> sum(List<Number> numbers) {
    double total = 0.0;
    for (double value : requireNumbers(numbers, "sum")) {
      total += value;
    }
    return Result.success(new ArithmeticOut(total));
  }

  private @NonNull Result<ArithmeticOut> min(List<Number> numbers) {
    double[] values = requireNumbers(numbers, "min");
    double result = values[0];
    for (int i = 1; i < values.length; i++) {
      result = Math.min(result, values[i]);
    }
    return Result.success(new ArithmeticOut(result));
  }

  private @NonNull Result<ArithmeticOut> max(List<Number> numbers) {
    double[] values = requireNumbers(numbers, "max");
    double result = values[0];
    for (int i = 1; i < values.length; i++) {
      result = Math.max(result, values[i]);
    }
    return Result.success(new ArithmeticOut(result));
  }

  private @NonNull Result<ArithmeticOut> mean(List<Number> numbers) {
    double[] values = requireNumbers(numbers, "mean");
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return Result.success(new ArithmeticOut(total / values.length));
  }

  private @NonNull Result<ArithmeticOut> median(List<Number> numbers) {
    double[] values = requireNumbers(numbers, "median");
    Arrays.sort(values);
    double result;
    if (values.length % 2 == 1) {
      result = values[values.length / 2];
    } else {
      int mid = values.length / 2;
      result = (values[mid - 1] + values[mid]) / 2.0;
    }
    return Result.success(new ArithmeticOut(result));
  }

  private @NonNull Result<ArithmeticOut> percentile(List<Number> numbers, Double p) {
    if (p == null) {
      throw new IllegalArgumentException("arithmetic.percentile: p is required");
    }
    if (p < 0 || p > 100) {
      throw new IllegalArgumentException("arithmetic.percentile: p must be between 0 and 100");
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
    return Result.success(new ArithmeticOut(result));
  }

  private @NonNull Result<ArithmeticOut> stddev(List<Number> numbers) {
    double[] values = requireNumbers(numbers, "stddev");
    if (values.length < 2) {
      throw new IllegalArgumentException("arithmetic.stddev: requires at least two numbers");
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
    return Result.success(new ArithmeticOut(Math.sqrt(sumOfSquares / (values.length - 1))));
  }

  private @NonNull Result<ArithmeticOut> clamp(
          Number value,
          Number min,
          Number max) {
    if (value == null) {
      throw new IllegalArgumentException("arithmetic.clamp: value is required");
    }
    if (min == null) {
      throw new IllegalArgumentException("arithmetic.clamp: min is required");
    }
    if (max == null) {
      throw new IllegalArgumentException("arithmetic.clamp: max is required");
    }
    double v = value.doubleValue();
    double lower = min.doubleValue();
    double upper = max.doubleValue();
    if (lower > upper) {
      throw new IllegalArgumentException("arithmetic.clamp: min cannot exceed max");
    }
    double result = Math.max(lower, Math.min(upper, v));
    return Result.success(new ArithmeticOut(result));
  }

  private @NonNull Result<ArithmeticOut> round(Number value, Integer scale) {
    if (value == null) {
      throw new IllegalArgumentException("arithmetic.round: value is required");
    }
    int effectiveScale = (scale == null) ? 0 : scale;
    if (effectiveScale < -1 || effectiveScale > 15) {
      throw new IllegalArgumentException("arithmetic.round: scale must be between -1 and 15");
    }
    double result = BigDecimal.valueOf(value.doubleValue())
            .setScale(effectiveScale, RoundingMode.HALF_UP)
            .doubleValue();
    return Result.success(new ArithmeticOut(result));
  }

  private @NonNull Result<ArithmeticOut> abs(Number value) {
    if (value == null) {
      throw new IllegalArgumentException("arithmetic.abs: value is required");
    }
    return Result.success(new ArithmeticOut(Math.abs(value.doubleValue())));
  }

  private @NonNull Result<ArithmeticOut> floor(Number value) {
    if (value == null) {
      throw new IllegalArgumentException("arithmetic.floor: value is required");
    }
    return Result.success(new ArithmeticOut((long) Math.floor(value.doubleValue())));
  }

  private @NonNull Result<ArithmeticOut> ceil(Number value) {
    if (value == null) {
      throw new IllegalArgumentException("arithmetic.ceil: value is required");
    }
    return Result.success(new ArithmeticOut((long) Math.ceil(value.doubleValue())));
  }

  private double[] requireNumbers(List<Number> numbers, String op) {
    if (numbers == null || numbers.isEmpty()) {
      throw new IllegalArgumentException("arithmetic." + op + ": numbers is required");
    }
    List<?> raw = numbers;
    double[] values = new double[raw.size()];
    for (int i = 0; i < raw.size(); i++) {
      Object element = raw.get(i);
      if (!(element instanceof Number number)) {
        throw new IllegalArgumentException(
                "arithmetic." + op + ": non-numeric element at index " + i + ": "
                        + preview(element));
      }
      values[i] = number.doubleValue();
    }
    return values;
  }

  private String preview(Object value) {
    if (value == null) {
      return "null";
    }
    String string = String.valueOf(value);
    return string.length() > 40 ? string.substring(0, 40) + "..." : string;
  }

  private @NonNull String describe(@NonNull String mode, @NonNull ArithmeticIn input) {
    return base(mode) + argsDetail(mode, input);
  }

  private @NonNull String base(@NonNull String mode) {
    return switch (mode) {
      case "sum" ->
        "arithmetic helper: computes the double sum of `numbers`. Requires non-empty `numbers`.";
      case "min" ->
        "arithmetic helper: returns the numerically smallest value of `numbers`. Requires non-empty `numbers`.";
      case "max" ->
        "arithmetic helper: returns the numerically largest value of `numbers`. Requires non-empty `numbers`.";
      case "mean" ->
        "arithmetic helper: computes the arithmetic mean of `numbers`. Requires non-empty `numbers`.";
      case "median" ->
        "arithmetic helper: computes the median of `numbers` (average of the two middle"
                + " values for even-length input). Requires non-empty `numbers`.";
      case "percentile" ->
        "arithmetic helper: computes the p-th percentile of `numbers` using linear"
                + " interpolation (Hyndman-Fan type 7). Requires non-empty `numbers` and `p` in [0,100].";
      case "stddev" -> "arithmetic helper: computes the sample standard deviation of `numbers`"
              + " (Bessel-corrected). Requires at least two numbers.";
      case "clamp" -> "arithmetic helper: clamps `value` into the inclusive [`min`, `max`] range."
              + " Requires `value`, `min`, and `max`.";
      case "round" -> "arithmetic helper: rounds `value` to `scale` decimal places (HALF_UP via"
              + " BigDecimal, default scale 0). Requires `value`.";
      case "abs" -> "arithmetic helper: returns the absolute value of `value`. Requires `value`.";
      case "floor" ->
        "arithmetic helper: returns the largest `long` not exceeding `value`. Requires `value`.";
      case "ceil" ->
        "arithmetic helper: returns the smallest `long` greater than or equal to `value`."
                + " Requires `value`.";
      default ->
        "arithmetic helper: unknown mode `" + mode + "`; expected one of sum, min, max, mean,"
                + " median, percentile, stddev, clamp, round, abs, floor, ceil.";
    };
  }

  private @NonNull String argsDetail(@NonNull String mode, @NonNull ArithmeticIn input) {
    return switch (mode) {
      case "percentile" -> input.p() != null ? " Args: p=" + input.p() + "." : "";
      case "round" -> input.scale() != null ? " Args: scale=" + input.scale() + "." : "";
      case "clamp" -> " Args: min=" + input.min() + ", max=" + input.max() + ".";
      default -> "";
    };
  }
}
