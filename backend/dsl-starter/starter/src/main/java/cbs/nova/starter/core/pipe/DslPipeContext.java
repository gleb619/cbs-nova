package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//TODO: redo to a `record` with lombok's builder
@RequiredArgsConstructor
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
    var copy = new DslPipeContext(name, dslContext, mode, runId);
    copy.attributes = this.attributes;
    return copy;
  }
}
