package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Builder
public record DslPipeContext(
        @NonNull String name,
        @NonNull Context<?> dslContext,
        @NonNull ExecutionMode mode,
        @NonNull String runId,
        @NonNull Map<String, Object> attributes) {

  public DslPipeContext {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(dslContext, "dslContext");
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(runId, "runId");
    attributes = attributes == null ? new ConcurrentHashMap<>() : attributes;
  }

  public static @NonNull DslPipeContext of(
          @NonNull String name,
          @NonNull Context<?> dslContext,
          @NonNull ExecutionMode mode,
          @NonNull String runId) {
    return new DslPipeContext(name, dslContext, mode, runId, new ConcurrentHashMap<>());
  }

  public @Nullable Object getAttribute(@NonNull String key) {
    return attributes.get(key);
  }

  @SuppressWarnings("unchecked")
  public <T> @Nullable T getAttribute(@NonNull String key, @NonNull Class<T> type) {
    Object value = attributes.get(key);
    return type.isInstance(value) ? (T) value : null;
  }

  public void setAttribute(@NonNull String key, @Nullable Object value) {
    if (value == null) {
      attributes.remove(key);
    } else {
      attributes.put(key, value);
    }
  }

  public @NonNull DslPipeContext withDslContext(@NonNull Context<?> dslContext) {
    return new DslPipeContext(name, dslContext, mode, runId, this.attributes);
  }
}
