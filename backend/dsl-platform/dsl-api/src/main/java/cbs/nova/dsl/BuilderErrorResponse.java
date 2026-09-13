package cbs.nova.dsl;

import static cbs.nova.dsl.config.Constants.FALLBACK_CODE;
import static cbs.nova.dsl.config.Constants.FALLBACK_MESSAGE;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record BuilderErrorResponse(
        @Nullable String code,
        @Nullable String message,
        @Nullable String error,
        @Nullable List<String> diagnostics) {

  public BuilderErrorResponse {
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }

  public String primaryCode() {
    return firstNonBlank(code, error, FALLBACK_CODE);
  }

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
