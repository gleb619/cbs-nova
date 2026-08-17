package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DslPipeContext {

  @Getter
  private final @NonNull String name;

  @Getter
  private final @NonNull Context<?> dslContext;

  @Getter
  private final @NonNull ExecutionMode mode;

  @Getter
  private final @NonNull String runId;

  private Map<String, Object> attributes = new ConcurrentHashMap<>();

  public DslPipeContext(
          @NonNull String name,
          @NonNull Context<?> dslContext,
          @NonNull ExecutionMode mode,
          @NonNull String runId) {
    this.name = name;
    this.dslContext = dslContext;
    this.mode = mode;
    this.runId = runId;
  }

  private DslPipeContext(
          @NonNull String name,
          @NonNull Context<?> dslContext,
          @NonNull ExecutionMode mode,
          @NonNull String runId,
          @NonNull Map<String, Object> attributes) {
    this.name = name;
    this.dslContext = dslContext;
    this.mode = mode;
    this.runId = runId;
    this.attributes = attributes;
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
    return new DslPipeContext(name, dslContext, mode, runId, attributes);
  }
}
