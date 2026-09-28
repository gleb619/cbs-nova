package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.CronParseIn;
import cbs.nova.starter.helper.model.CronParseOut;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.support.CronExpression;

/**
 * Validates cron expressions and previews their next fire times using Spring's
 * {@link org.springframework.scheduling.support.CronExpression}.
 *
 * <p>
 * Two modes are supported, selected by the {@code mode} discriminator (case-insensitive):
 * <ul>
 * <li>{@code "validate"} — checks that {@code expression} is a valid cron string. A valid
 * expression returns {@code valid = true}; an invalid expression returns a
 * {@link Result#failure(Throwable)} with a clear message.</li>
 * <li>{@code "nextFire"} — computes the next fire times after an optional {@code after} timestamp
 * (default {@code Instant.now()}). The optional {@code count} controls how many fire times are
 * returned (default {@code 1}). Each fire time is returned as an ISO-8601 instant string.</li>
 * </ul>
 *
 * <p>
 * The DST-safe 6-field form ({@code sec min hour dom mon dow}) is passed straight through. Common
 * 5-field Unix forms ({@code min hour dom mon dow}) are normalized by prepending a {@code 0}
 * seconds field. Macros such as {@code @yearly}, {@code @daily}, and {@code @hourly} are also
 * accepted. Times are evaluated as UTC wall-clock times; the returned instants are
 * {@code Z}-normalized ISO-8601 strings.
 */
@Helper(name = "cronParse")
public class CronParseHelper implements Executable<CronParseIn, CronParseOut> {

  private static final int UNIX_FIELD_COUNT = 5;

  @Override
  public @NonNull Result<CronParseOut> execute(@NonNull Context<CronParseIn> ctx) {
    CronParseIn input = ctx.body();
    try {
      if (input.expression() == null || input.expression().isBlank()) {
        return Result.failure(new IllegalArgumentException("cronParse.expression is required"));
      }
      String mode = (input.mode() == null) ? "validate" : input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "validate" -> doValidate(input.expression());
        case "nextfire" -> doNextFire(input);
        case null, default -> Result.failure(new IllegalArgumentException(
                "cronParse.mode must be 'validate' or 'nextFire', was: " + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static Result<CronParseOut> doValidate(String expression) {
    parseCron(expression);
    return Result.success(new CronParseOut(true, null, null));
  }

  private static Result<CronParseOut> doNextFire(CronParseIn input) {
    CronExpression cron = parseCron(input.expression());
    LocalDateTime start = parseAfter(input.after());
    int count = (input.count() == null || input.count() < 1) ? 1 : input.count();

    List<String> timestamps = new ArrayList<>(count);
    LocalDateTime current = start;
    for (int i = 0; i < count; i++) {
      LocalDateTime next = cron.next(current);
      if (next == null) {
        throw new IllegalArgumentException(
                "cronParse: expression '" + input.expression()
                        + "' produced no next fire time after " + current);
      }
      timestamps.add(formatInstant(next));
      current = next;
    }
    return Result.success(new CronParseOut(true, List.copyOf(timestamps), null));
  }

  private static CronExpression parseCron(String expression) {
    try {
      String normalized = normalizeExpression(expression.trim());
      return CronExpression.parse(normalized);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
              "cronParse: invalid cron expression '" + expression + "': " + e.getMessage(), e);
    }
  }

  private static String normalizeExpression(String expression) {
    // Macros such as @daily do not contain whitespace and are passed through unchanged.
    if (expression.startsWith("@")) {
      return expression;
    }
    int fieldCount = countFields(expression);
    if (fieldCount == UNIX_FIELD_COUNT) {
      return "0 " + expression;
    }
    return expression;
  }

  private static int countFields(String expression) {
    int count = 1;
    for (int i = 0; i < expression.length(); i++) {
      if (Character.isWhitespace(expression.charAt(i))) {
        count++;
      }
    }
    return count;
  }

  private static LocalDateTime parseAfter(@Nullable String after) {
    if (after == null || after.isBlank()) {
      return LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    }
    try {
      Instant instant = Instant.parse(after.trim());
      return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
              "cronParse.after must be an ISO-8601 instant, was: " + after, e);
    }
  }

  private static String formatInstant(LocalDateTime dateTime) {
    Instant instant = dateTime.toInstant(ZoneOffset.UTC);
    return DateTimeFormatter.ISO_INSTANT.format(instant);
  }
}
