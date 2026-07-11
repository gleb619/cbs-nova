package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public interface Context<T> {

  @NonNull
  T body();

  @NonNull
  Map<String, Object> metadata();

  @NonNull
  ExecutionMode mode();

  @NonNull
  String runId();

  @NonNull
  <U> Context<U> withBody(@NonNull U body);

  @NonNull
  Context<T> withMetadata(@NonNull String key, @Nullable Object value);
}
