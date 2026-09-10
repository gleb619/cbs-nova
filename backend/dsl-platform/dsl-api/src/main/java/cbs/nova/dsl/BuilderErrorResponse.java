package cbs.nova.dsl;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Typed representation of an error payload returned by the DSL builder service.
 *
 * <p>The builder emits two wire shapes depending on the endpoint:
 *
 * <ul>
 *   <li>{@code { code, message, diagnostics? }} for general {@code BuilderApiException}s.
 *   <li>{@code { error, diagnostics }} for {@code /api/dsl/compile} failures ({@code
 *       CompileErrorResponse}), where {@code error} carries the machine code and {@code message}
 *       is folded into {@code diagnostics[0]}.
 * </ul>
 *
 * <p>All fields are nullable so a single record can deserialize either shape. Callers should use
 * {@link #primaryCode()} and {@link #primaryMessage()} to pick the right value regardless of shape.
 *
 * @param code machine-readable error code (general shape)
 * @param message human-readable message (general shape)
 * @param error machine-readable error code (compile shape)
 * @param diagnostics list of diagnostic strings; for compile failures the first entry is the
 *     human message
 */
public record BuilderErrorResponse(
        @Nullable String code,
        @Nullable String message,
        @Nullable String error,
        @Nullable List<String> diagnostics) {

  public static final String FALLBACK_CODE = "BUILDER_ERROR";
  public static final String FALLBACK_MESSAGE = "builder request failed";

  public BuilderErrorResponse {
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }

  /** Returns the first non-blank code from {@link #code} or {@link #error}, or the fallback. */
  public String primaryCode() {
    return firstNonBlank(code, error, FALLBACK_CODE);
  }

  /**
   * Returns the first non-blank human message. For compile-shaped responses (where {@code message}
   * is absent) it falls back to {@code diagnostics[0]} and finally the fallback constant.
   */
  public String primaryMessage() {
    String firstDiagnostic = diagnostics.isEmpty() ? null : diagnostics.get(0);
    return firstNonBlank(message, firstDiagnostic, FALLBACK_MESSAGE);
  }

  private static String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return null;
  }
}
