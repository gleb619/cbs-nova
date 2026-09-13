package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.MaskIn;
import cbs.nova.starter.helper.model.MaskOut;
import java.util.Locale;
import org.jspecify.annotations.NonNull;

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
