package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record PreviewErrorDetail(
        @NonNull PreviewErrorCode code,
        @NonNull String message,
        @NonNull String suggestion,
        @Nullable Map<String, Object> context) {

  public PreviewErrorDetail {
    context = context == null ? Map.of() : Map.copyOf(context);
  }
}
