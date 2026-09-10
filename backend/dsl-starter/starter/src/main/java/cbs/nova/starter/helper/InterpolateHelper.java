package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.InterpolateIn;
import cbs.nova.starter.helper.model.InterpolateOut;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Safe literal {@code ${key}} string templating — no expression evaluation.
 *
 * <p>
 * Pairs with {@link FormatMessageHelper}, which evaluates the template as a single SpEL expression
 * (powerful but unsafe for operator- or user-supplied templates). Use {@code interpolate} when the
 * template is configuration data that must not be able to call methods. Phase 1 supports flat keys
 * only — dotted paths ({@code ${order.id}}) and default-value syntax ({@code ${name:-anon}}) are
 * deliberate follow-ups.
 *
 * <p>
 * Rules:
 * <ul>
 * <li>{@code ${key}} is substituted from {@code params}; the key is {@code trim()}-ed.</li>
 * <li>{@code $${} renders a literal {@code $} (i.e. {@code "$${x}"} → {@code "${x}"} when {@code x}
 * is unknown or always produces a literal leading {@code $} when followed by a placeholder).</li>
 * <li>{@code ${}} (empty key) and an unclosed {@code ${} (no matching {@code }}) fail.</li>
 * <li>Braces inside keys are not supported — the first {@code }} closes the placeholder, so
 * {@code ${a${b}} substitutes key {@code a${b}.</li>
 * <li>A value present in the map but mapped to {@code null} renders as {@code ""}, even under
 * {@code onMissing=error}. "Missing" means absent from the map, not {@code null}-valued.</li>
 * </ul>
 */
@Helper(name = "interpolate")
public class InterpolateHelper implements Executable<InterpolateIn, InterpolateOut> {

  @Override
  public @NonNull Result<InterpolateOut> execute(@NonNull Context<InterpolateIn> ctx) {
    try {
      InterpolateIn input = ctx.body();
      if (input.template() == null) {
        return Result.failure(new IllegalArgumentException("interpolate.template is required"));
      }
      Map<String, Object> params = input.params() == null ? Map.of() : input.params();
      String policy = normalizeOnMissing(input.onMissing());
      if (policy == null) {
        return Result.failure(new IllegalArgumentException(
                "interpolate.onMissing must be 'error', 'empty', or 'keep', was: "
                        + input.onMissing()));
      }

      String template = input.template();
      StringBuilder out = new StringBuilder(template.length());
      Set<String> seenKeys = new LinkedHashSet<>();

      int i = 0;
      int len = template.length();
      while (i < len) {
        char c = template.charAt(i);
        if (c == '$' && i + 1 < len && template.charAt(i + 1) == '$') {
          // '$${' escape: emit literal '${' and skip the leading '$'.
          // If the following char is not '{', still emit both '$' characters literally
          // (we only consumed two chars).
          out.append('$');
          i += 2;
          continue;
        }
        if (c == '$' && i + 1 < len && template.charAt(i + 1) == '{') {
          int close = template.indexOf('}', i + 2);
          if (close < 0) {
            return Result.failure(new IllegalArgumentException(
                    "interpolate: unclosed '${' at offset " + i));
          }
          String rawKey = template.substring(i + 2, close).trim();
          if (rawKey.isEmpty()) {
            return Result.failure(new IllegalArgumentException(
                    "interpolate: empty key at offset " + i));
          }
          String key = rawKey;
          if (params.containsKey(key)) {
            Object value = params.get(key);
            out.append(formatValue(value));
            seenKeys.add(key);
          } else {
            switch (policy) {
              case "empty" -> out.append("");
              case "keep" -> out.append(template, i, close + 1);
              default -> {
                return Result.failure(new IllegalArgumentException(
                        "interpolate: missing key '" + key + "'"));
              }
            }
          }
          i = close + 1;
          continue;
        }
        out.append(c);
        i++;
      }

      return Result.success(new InterpolateOut(out.toString(), List.copyOf(seenKeys)));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  /**
   * Normalises the {@code onMissing} policy to its lower-case canonical form. Returns
   * {@code "error"} when the input is {@code null} (default), or {@code null} if the value is
   * unrecognised.
   */
  private static String normalizeOnMissing(String raw) {
    if (raw == null) {
      return "error";
    }
    String lower = raw.toLowerCase(Locale.ROOT);
    return switch (lower) {
      case "error", "empty", "keep" -> lower;
      default -> null;
    };
  }

  private static String formatValue(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof BigDecimal bd) {
      return bd.stripTrailingZeros().toPlainString();
    }
    return String.valueOf(value);
  }
}
