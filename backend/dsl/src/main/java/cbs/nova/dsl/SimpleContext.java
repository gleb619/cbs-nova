package cbs.nova.dsl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record SimpleContext<T>(
        @NonNull T body,
        @NonNull Map<String, Object> metadata,
        @NonNull ExecutionMode mode,
        @NonNull String runId)
        implements
          Context<T> {

  private static final SimpleContext<?> INSTANCE = new SimpleContext<>(null, Map.of(), null, null);

  public static SimpleContext<?> getInstance() {
    return INSTANCE;
  }

  public <U> Context<U> withBody(@NonNull U newBody) {
    return new SimpleContext<>(newBody, metadata, mode, runId);
  }

  public Context<T> withMetadata(@NonNull String key, @Nullable Object value) {
    var updated = new HashMap<>(metadata);
    updated.put(key, value);
    return new SimpleContext<>(body, Map.copyOf(updated), mode, runId);
  }

  public <U> SimpleContext<U> of(@NonNull U body, @NonNull ExecutionMode mode) {
    return new SimpleContext<>(body, Map.of(), mode, generateRunId());
  }

  public <U> SimpleContext<U> of(
          @NonNull U body, @NonNull ExecutionMode mode, @NonNull String runId) {
    return new SimpleContext<>(body, Map.of(), mode, runId);
  }

  public <U> SimpleContext<U> of(
          @NonNull U body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId) {
    return new SimpleContext<>(body, metadata, mode, runId);
  }

  public String generateRunId() {
    return "run-" + UUID.randomUUID();
  }
}
