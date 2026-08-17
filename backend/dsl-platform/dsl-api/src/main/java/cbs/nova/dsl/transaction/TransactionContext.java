package cbs.nova.dsl.transaction;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.MapInput;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public interface TransactionContext<T> extends Context<T> {

  @NonNull
  Result<?> runHelper(@NonNull String name);

  @NonNull
  Result<?> runHelper(@NonNull String name, @NonNull Map<String, Object> input);

  @NonNull
  Result<?> runHelper(@NonNull String name, @NonNull MapInput input);
}
