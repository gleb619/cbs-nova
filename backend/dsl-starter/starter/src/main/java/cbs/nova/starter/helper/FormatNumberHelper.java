package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.FormatNumberIn;
import cbs.nova.starter.helper.model.FormatNumberOut;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.IllformedLocaleException;
import java.util.Locale;
import org.jspecify.annotations.NonNull;

/**
 * Formats a numeric value into a locale-aware string using a preset or a custom
 * {@link java.text.DecimalFormat} pattern.
 *
 * <p>
 * The source value can be supplied as:
 * <ul>
 * <li>a {@link Number} such as {@link java.math.BigDecimal}, {@link Double}, {@link Long},
 * ...&lt;/li>
 * <li>a numeric {@link String}; scientific notation ({@code 1.23E+4}) is accepted&lt;/li>
 * </ul>
 *
 * <p>
 * Supported preset pattern aliases (case-sensitive) are {@code INTEGER}, {@code DECIMAL} (grouping
 * separator + two fraction digits), {@code PERCENT} and {@code CURRENCY}. Any other value is
 * treated as a raw {@link java.text.DecimalFormat} pattern resolved against the requested locale.
 *
 * <p>
 * Rounding follows {@link RoundingMode#HALF_UP}. Invalid locale tags, invalid patterns and
 * non-numeric input produce a failed {@link Result} instead of throwing raw.
 */
@Helper(name = "formatNumber")
public class FormatNumberHelper implements Executable<FormatNumberIn, FormatNumberOut> {

  @Override
  public @NonNull Result<FormatNumberOut> execute(@NonNull Context<FormatNumberIn> ctx) {
    try {
      FormatNumberIn input = ctx.body();
      BigDecimal value = parseNumber(input.input());
      Locale locale = resolveLocale(input.locale());
      DecimalFormat formatter = buildFormatter(input.pattern(), locale);
      return Result.success(new FormatNumberOut(formatter.format(value)));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static BigDecimal parseNumber(Object input) {
    if (input == null) {
      throw new IllegalArgumentException("formatNumber.input is required");
    }
    if (input instanceof Number number) {
      return toBigDecimal(number);
    }
    if (input instanceof String string) {
      String trimmed = string.trim();
      if (trimmed.isEmpty()) {
        throw new IllegalArgumentException("formatNumber.input is required");
      }
      try {
        return new BigDecimal(trimmed);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
                "formatNumber.input is not a recognized number: " + string, e);
      }
    }
    throw new IllegalArgumentException(
            "formatNumber.input must be a Number or numeric string, got "
                    + input.getClass().getSimpleName());
  }

  private static BigDecimal toBigDecimal(Number value) {
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    if (value instanceof BigInteger integer) {
      return new BigDecimal(integer);
    }
    if (value instanceof Double doubleValue) {
      if (doubleValue.isNaN() || doubleValue.isInfinite()) {
        throw new IllegalArgumentException(
                "formatNumber.input is not a finite number: " + doubleValue);
      }
      return BigDecimal.valueOf(doubleValue);
    }
    if (value instanceof Float floatValue) {
      if (floatValue.isNaN() || floatValue.isInfinite()) {
        throw new IllegalArgumentException(
                "formatNumber.input is not a finite number: " + floatValue);
      }
      return BigDecimal.valueOf(floatValue);
    }
    if (value instanceof Number) {
      return BigDecimal.valueOf(value.longValue());
    }
    throw new IllegalArgumentException(
            "formatNumber.input is not a recognized number: " + value);
  }

  private static Locale resolveLocale(String locale) {
    if (locale == null || locale.isBlank()) {
      return Locale.ROOT;
    }
    String trimmed = locale.trim();
    try {
      return new Locale.Builder().setLanguageTag(trimmed).build();
    } catch (IllformedLocaleException e) {
      throw new IllegalArgumentException("formatNumber.locale is not a valid BCP-47 tag: " + locale,
              e);
    }
  }

  private static DecimalFormat buildFormatter(String pattern, Locale locale) {
    if (pattern == null || pattern.isBlank()) {
      throw new IllegalArgumentException("formatNumber.pattern is required");
    }
    String rawPattern = resolvePreset(pattern.trim());
    try {
      DecimalFormat formatter = new DecimalFormat(rawPattern, new DecimalFormatSymbols(locale));
      formatter.setRoundingMode(RoundingMode.HALF_UP);
      return formatter;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("formatNumber.pattern is invalid: " + pattern, e);
    }
  }

  private static String resolvePreset(String pattern) {
    return switch (pattern) {
      case "INTEGER" -> "#,##0";
      case "DECIMAL" -> "#,##0.00";
      case "PERCENT" -> "#,##0.00%";
      case "CURRENCY" -> "¤#,##0.00";
      default -> pattern;
    };
  }
}
