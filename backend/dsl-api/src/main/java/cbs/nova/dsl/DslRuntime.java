package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public interface DslRuntime {
  @NonNull
  Result<?> preview(@NonNull String name, @NonNull Context<?> ctx);

  @NonNull
  Result<?> run(@NonNull String name, @NonNull Context<?> ctx);

  @NonNull
  ExplainReport explain(@NonNull String name, @NonNull Context<?> ctx);
}
