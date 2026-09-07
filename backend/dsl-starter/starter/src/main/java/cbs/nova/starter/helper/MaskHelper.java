package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.MaskIn;
import cbs.nova.starter.helper.model.MaskOut;
import java.util.Locale;
import org.jspecify.annotations.NonNull;

/**
 * Redacts sensitive values (card numbers, IBANs, emails, credentials) so they can be safely
 * embedded in log lines, notification messages, or audit {@code details_json}.
 *
 * <p>
 * Two modes, selected by {@code MaskIn.mode()} (case-insensitive):
 * <ul>
 * <li>{@code null} or {@code "edges"} — keep the configured edges visible and mask the middle.
 *   When neither {@code keepFirst} nor {@code keepLast} is given, the <b>safe default</b>
 *   applies: a value of {@value #SAFE_DEFAULT_THRESHOLD} or more Unicode code points keeps the
 *   last {@value #SAFE_DEFAULT_KEEP_LAST} code points visible and masks everything before them
 *   (e.g. {@code "4111111111111111" → "************1111"}); a shorter value is replaced by
 *   exactly {@value #DEFAULT_FIXED_WIDTH} mask characters, so the true length never leaks.
 *   With explicit {@code keepFirst}/{@code keepLast}, those edges stay visible and the middle is
 *   masked.</li>
 * <li>{@code "fixed"} — the output is exactly {@code width} mask characters (default
 *   {@value #DEFAULT_FIXED_WIDTH}), independent of the input length.</li>
 * </ul>
 *
 * <p>
 * Conventions:
 * <ul>
 * <li>A {@code null} value is rejected with an {@link IllegalArgumentException} (same convention
 *   as {@code hex}: {@code "mask.value is required"}); an empty value returns an empty
 *   result.</li>
 * <li>{@code maskChar} defaults to {@value #DEFAULT_MASK_CHAR}; when more than one character is
 *   supplied, the first character is used.</li>
 * <li>{@code keepFirst}/{@code keepLast} are counted in Unicode code points (surrogate pairs are
 *   never split); negative values are treated as {@code 0}.</li>
 * <li>Clamp rule: {@code keepFirst + keepLast >= codePointCount} never returns the value
 *   unmasked — the keeps are reduced so exactly one code point stays masked.</li>
 * <li>{@code width < 1} is rejected with an {@link IllegalArgumentException}.</li>
 * </ul>
 */
@Helper(name = "mask")
public class MaskHelper implements Executable<MaskIn, MaskOut> {

  static final int DEFAULT_FIXED_WIDTH = 8;
  static final int SAFE_DEFAULT_THRESHOLD = 8;
  static final int SAFE_DEFAULT_KEEP_LAST = 4;
  static final char DEFAULT_MASK_CHAR = '*';

  @Override
  public @NonNull Result<MaskOut> execute(@NonNull Context<MaskIn> ctx) {
    try {
      MaskIn input = ctx.body();
      if (input.value() == null) {
        return Result.failure(new IllegalArgumentException("mask.value is required"));
      }
      if (input.value().isEmpty()) {
        return Result.success(new MaskOut(""));
      }
      char maskChar = maskChar(input.maskChar());
      String mode = input.mode() == null ? "edges" : input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "edges" -> Result.success(new MaskOut(maskEdges(input, maskChar)));
        case "fixed" -> Result.success(new MaskOut(maskFixed(input, maskChar)));
        default -> Result.failure(
                new IllegalArgumentException(
                        "mask.mode must be 'edges' or 'fixed', was: " + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static char maskChar(String maskChar) {
    return (maskChar == null || maskChar.isEmpty()) ? DEFAULT_MASK_CHAR : maskChar.charAt(0);
  }

  private static String maskEdges(MaskIn input, char maskChar) {
    int[] codePoints = input.value().codePoints().toArray();
    int length = codePoints.length;
    if (input.keepFirst() == null && input.keepLast() == null) {
      // safe default: never leak the true length of short values
      if (length >= SAFE_DEFAULT_THRESHOLD) {
        return build(codePoints, 0, SAFE_DEFAULT_KEEP_LAST, maskChar);
      }
      return repeat(maskChar, DEFAULT_FIXED_WIDTH);
    }
    int keepFirst = Math.max(0, input.keepFirst() == null ? 0 : input.keepFirst());
    int keepLast = Math.max(0, input.keepLast() == null ? 0 : input.keepLast());
    // clamp rule: at least one code point must always stay masked
    if (keepFirst + keepLast >= length) {
      if (keepFirst >= length) {
        keepFirst = length - 1;
        keepLast = 0;
      } else {
        keepLast = length - keepFirst - 1;
      }
    }
    return build(codePoints, keepFirst, keepLast, maskChar);
  }

  private static String maskFixed(MaskIn input, char maskChar) {
    int width = input.width() == null ? DEFAULT_FIXED_WIDTH : input.width();
    if (width < 1) {
      throw new IllegalArgumentException("mask.width must be >= 1");
    }
    return repeat(maskChar, width);
  }

  private static String build(int[] codePoints, int keepFirst, int keepLast, char maskChar) {
    return new String(codePoints, 0, keepFirst)
            + repeat(maskChar, codePoints.length - keepFirst - keepLast)
            + new String(codePoints, codePoints.length - keepLast, keepLast);
  }

  private static String repeat(char c, int count) {
    return String.valueOf(c).repeat(count);
  }
}
