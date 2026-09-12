package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.ParseDurationIn;
import cbs.nova.starter.helper.model.ParseDurationOut;
import java.math.BigInteger;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/**
 * Parses a duration string into total milliseconds, floored seconds, and a normalized ISO-8601
 * duration string.
 *
 * <p>
 * Two forms are accepted, case-insensitively and after trimming:
 * <ul>
 * <li>ISO-8601 duration strings parseable by {@link java.time.Duration#parse(String)}, such as
 * {@code "PT1H30M"}, {@code "P2DT3H"}, or {@code "PT0.5S"}. Bare day forms such as {@code "P2D"}
 * normalize to a time component ({@code "PT48H"}).</li>
 * <li>Shorthand made of one or more whole-number {@code <number><unit>} segments, where the unit is
 * one of {@code d} (days), {@code h}, {@code m} (minutes), {@code s}, or {@code ms}. Whitespace
 * between segments is tolerated. Examples: {@code "90m"}, {@code "1h30m"}, {@code "2d12h"},
 * {@code "250ms"}, {@code "45s"}.</li>
 * </ul>
 *
 * <p>
 * A bare number with no unit is rejected as ambiguous; {@code m} always means minutes, never
 * months; and signed/negative durations are rejected in this phase. See the follow-up item for
 * signed offsets. Overflow past {@link Long#MAX_VALUE} milliseconds is rejected cleanly.
 */
@Helper(name = "parseDuration")
public class ParseDurationHelper implements Executable<ParseDurationIn, ParseDurationOut> {

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private static final BigInteger MS_PER_MS = BigInteger.ONE;
  private static final BigInteger MS_PER_S = BigInteger.valueOf(1_000L);
  private static final BigInteger MS_PER_M = BigInteger.valueOf(60_000L);
  private static final BigInteger MS_PER_H = BigInteger.valueOf(3_600_000L);
  private static final BigInteger MS_PER_D = BigInteger.valueOf(86_400_000L);
  private static final BigInteger MAX_MILLIS = BigInteger.valueOf(Long.MAX_VALUE);

  @Override
  public @NonNull Result<ParseDurationOut> execute(@NonNull Context<ParseDurationIn> ctx) {
    ParseDurationIn input = ctx.body();
    try {
      if (input.value() == null || input.value().isBlank()) {
        return Result.failure(new IllegalArgumentException("parseDuration.value is required"));
      }
      String trimmed = input.value().trim();
      if (trimmed.isEmpty()) {
        return Result.failure(new IllegalArgumentException("parseDuration.value is required"));
      }
      char first = trimmed.charAt(0);
      if (first == '+' || first == '-') {
        return Result.failure(new IllegalArgumentException(
                "parseDuration: signed durations are not supported in phase 1, was: " + trimmed));
      }

      Duration duration = (first == 'P' || first == 'p')
              ? parseIso(trimmed)
              : parseShorthand(trimmed);

      if (duration.isNegative()) {
        return Result.failure(new IllegalArgumentException(
                "parseDuration: negative durations are not supported in phase 1, was: " + trimmed));
      }

      long millis = toMillis(duration, trimmed);
      return Result.success(new ParseDurationOut(
              millis,
              duration.toSeconds(),
              duration.toString()));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static Duration parseIso(String value) {
    try {
      return Duration.parse(value);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
              "parseDuration: invalid ISO-8601 duration: " + value, e);
    }
  }

  private static Duration parseShorthand(String value) {
    String normalized = WHITESPACE.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("");
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("parseDuration.value is required");
    }

    BigInteger millis = BigInteger.ZERO;
    int pos = 0;
    int len = normalized.length();
    while (pos < len) {
      int numEnd = pos;
      while (numEnd < len && Character.isDigit(normalized.charAt(numEnd))) {
        numEnd++;
      }
      if (numEnd == pos) {
        throw new IllegalArgumentException(
                "parseDuration: expected number at position " + pos + " in shorthand: " + value);
      }

      String unit;
      int unitLen;
      if (normalized.startsWith("ms", numEnd)) {
        unit = "ms";
        unitLen = 2;
      } else if (numEnd < len) {
        char c = normalized.charAt(numEnd);
        switch (c) {
          case 'd', 'h', 'm', 's' -> {
            unit = String.valueOf(c);
            unitLen = 1;
          }
          default -> throw new IllegalArgumentException(
                  "parseDuration: unknown unit '" + c + "' in shorthand: " + value);
        }
      } else {
        throw new IllegalArgumentException(
                "parseDuration: missing unit after number in shorthand: " + value);
      }

      BigInteger count = new BigInteger(normalized.substring(pos, numEnd));
      BigInteger multiplier = switch (unit) {
        case "d" -> MS_PER_D;
        case "h" -> MS_PER_H;
        case "m" -> MS_PER_M;
        case "s" -> MS_PER_S;
        case "ms" -> MS_PER_MS;
        default -> throw new IllegalStateException("Unexpected unit: " + unit);
      };
      millis = millis.add(count.multiply(multiplier));

      pos = numEnd + unitLen;
    }

    if (millis.compareTo(MAX_MILLIS) > 0) {
      throw new IllegalArgumentException(
              "parseDuration: duration exceeds Long.MAX_VALUE milliseconds: " + value);
    }
    return Duration.ofMillis(millis.longValueExact());
  }

  private static long toMillis(Duration duration, String value) {
    try {
      return duration.toMillis();
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(
              "parseDuration: duration exceeds Long.MAX_VALUE milliseconds: " + value, e);
    }
  }
}
