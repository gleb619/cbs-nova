package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record SimpleContext<T>(
        @NonNull T body,
        @NonNull Map<String, Object> metadata,
        @NonNull ExecutionMode mode,
        @NonNull String runId)
        implements
          Context<T> {

  public <U> Context<U> withBody(@NonNull U newBody) {
    return new SimpleContext<>(newBody, metadata, mode, runId);
  }

  public Context<T> withMetadata(@NonNull String key, @Nullable Object value) {
    var updated = new HashMap<>(metadata);
    updated.put(key, value);
    return new SimpleContext<>(body, Map.copyOf(updated), mode, runId);
  }

  public static <T> SimpleContext<T> of(@NonNull T body, @NonNull ExecutionMode mode) {
    return new SimpleContext<>(body, Map.of(), mode, generateRunId());
  }

  public static <T> SimpleContext<T> of(
          @NonNull T body, @NonNull ExecutionMode mode, @NonNull String runId) {
    return new SimpleContext<>(body, Map.of(), mode, runId);
  }

  public static <T> SimpleContext<T> of(
          @NonNull T body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId) {
    return new SimpleContext<>(body, metadata, mode, runId);
  }

  public static String generateRunId() {
    return "run-" + UUID.randomUUID();
  }
}
