package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.Map;

public interface CompensationContext<T> extends Context<T> {

  @NonNull
  Throwable error();

  @NonNull
  Result<?> runHelper(@NonNull String name);

  @NonNull
  Result<?> runHelper(@NonNull String name, @NonNull Map<String, Object> input);

  void log(@NonNull String message);
}
