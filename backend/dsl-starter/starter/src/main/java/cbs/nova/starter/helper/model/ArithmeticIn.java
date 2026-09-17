package cbs.nova.starter.helper.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Input for the built-in {@code arithmetic} helper.
 *
 * <p>
 * The helper supports two calling styles:
 * <ul>
 * <li><strong>Math modes</strong> — use {@code mode} with {@code numbers} / scalar fields. These
 * are the modes historically provided by the deprecated {@code math} helper: {@code sum},
 * {@code min}, {@code max}, {@code mean}, {@code median}, {@code percentile}, {@code stddev},
 * {@code clamp}, {@code round}, {@code abs}, {@code floor}, {@code ceil}.</li>
 * <li><strong>Legacy arithmetic operations</strong> — use {@code operation} with {@code values} (or
 * {@code numbers}). Supported operations are {@code ADD}, {@code SUBTRACT}, {@code MULTIPLY},
 * {@code DIVIDE}, {@code MIN}, {@code MAX}. This preserves the original {@code sumValues} helper
 * shape.</li>
 * </ul>
 * When both are present, {@code mode} wins.
 */
public record ArithmeticIn(
        @Nullable String mode,
        @Nullable String operation,
        @Nullable List<Number> numbers,
        @Nullable List<Number> values,
        @Nullable Number value,
        @Nullable Number min,
        @Nullable Number max,
        @Nullable Integer scale,
        @Nullable Double p) {

  public @Nullable Operation effectiveOperation() {
    if (operation == null || operation.isBlank()) {
      return null;
    }
    try {
      return Operation.valueOf(operation.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public @Nullable List<Number> effectiveNumbers() {
    return numbers != null ? numbers : values;
  }

  public enum Operation {
    ADD, SUBTRACT, MULTIPLY, DIVIDE, MIN, MAX
  }
}
