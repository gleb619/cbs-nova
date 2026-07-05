package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public record SimpleContext<T>(
        @NonNull T body, @NonNull Map<String, Object> metadata, @NonNull ExecutionMode mode)
        implements
          Context<T> {

  public <U> Context<U> withBody(@NonNull U newBody) {
    return new SimpleContext<>(newBody, metadata, mode);
  }

  public Context<T> withMetadata(@NonNull String key, @Nullable Object value) {
    var updated = new HashMap<>(metadata);
    updated.put(key, value);
    return new SimpleContext<>(body, Map.copyOf(updated), mode);
  }

  public static <T> SimpleContext<T> of(@NonNull T body, @NonNull ExecutionMode mode) {
    return new SimpleContext<>(body, Map.of(), mode);
  }
}
