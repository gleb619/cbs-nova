package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable context implementation. If the stored body is a {@link MapInput}, {@link #body()}
 * returns a fresh copy of the underlying map so DSL parameter blocks always see a mutable map.
 */
public final class SimpleContext<T> implements Context<T> {

  private final Object body;
  private final Map<String, Object> metadata;
  private final ExecutionMode mode;
  private final String runId;

  public SimpleContext(
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId) {
    this.body = body;
    this.metadata = metadata;
    this.mode = mode;
    this.runId = runId;
  }

  @Override
  @SuppressWarnings("unchecked")
  public @NonNull T body() {
    return switch (body) {
      case MapInput mapInput -> (T) mapInput.asMap();
      default -> (T) body;
    };
  }

  @Override
  public @NonNull Map<String, Object> metadata() {
    return metadata;
  }

  @Override
  public @NonNull ExecutionMode mode() {
    return mode;
  }

  @Override
  public @NonNull String runId() {
    return runId;
  }

  @Override
  public <U> @NonNull Context<U> withBody(@NonNull U newBody) {
    return new SimpleContext<>(newBody, metadata, mode, runId);
  }

  @Override
  public @NonNull Context<T> withMetadata(@NonNull String key, @Nullable Object value) {
    var updated = new LinkedHashMap<>(metadata);
    updated.put(key, value);
    return new SimpleContext<>(body, Map.copyOf(updated), mode, runId);
  }
}
