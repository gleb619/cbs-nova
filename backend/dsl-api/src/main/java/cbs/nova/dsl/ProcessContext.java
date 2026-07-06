package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.Map;

public interface ProcessContext<T> extends Context<T> {

  @NonNull
  Result<?> runHelper(@NonNull String name);

  @NonNull
  Result<?> runHelper(@NonNull String name, @NonNull Map<String, Object> input);

  @NonNull
  Result<?> runHelper(@NonNull String name, @NonNull Object input);

  @NonNull
  Result<?> runTransaction(@NonNull String name);

  @NonNull
  Result<?> runTransaction(@NonNull String name, @NonNull Map<String, Object> input);

  @NonNull
  Result<?> runTransaction(@NonNull String name, @NonNull Object input);

  @NonNull
  Result<?> complete(@NonNull Object result);

  void fail(@NonNull String reason);

  void log(@NonNull String message);
}
