package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public record SimpleContext<T>(
        @NonNull T body,
        @NonNull Map<String, Object> metadata,
        @NonNull ExecutionMode mode,
        @NonNull String runId)
        implements
          Context<T> {

  @Override
  public <U> Context<U> withBody(@NonNull U newBody) {
    return new SimpleContext<>(newBody, metadata, mode, runId);
  }

  @Override
  public Context<T> withMetadata(@NonNull String key, @Nullable Object value) {
    var updated = new HashMap<>(metadata);
    updated.put(key, value);
    return new SimpleContext<>(body, Map.copyOf(updated), mode, runId);
  }
}
