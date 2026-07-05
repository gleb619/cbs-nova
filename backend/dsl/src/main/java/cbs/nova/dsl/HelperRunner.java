package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public interface HelperRunner {
  @NonNull
  Result<?> runHelper(@NonNull String name, @NonNull Context<?> ctx,
          @NonNull HelperRegistry registry);

  @NonNull
  Result<?> runFunction(@NonNull String name, @NonNull Context<?> ctx,
          @NonNull HelperRegistry registry);
}
