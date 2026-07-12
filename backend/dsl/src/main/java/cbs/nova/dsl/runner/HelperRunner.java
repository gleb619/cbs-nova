package cbs.nova.dsl.runner;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.registry.HelperRegistry;
import org.jspecify.annotations.NonNull;

public interface HelperRunner {

  @NonNull
  Result<?> runHelper(@NonNull String name, @NonNull Context<?> ctx,
          @NonNull HelperRegistry registry);

  @NonNull
  Result<?> runFunction(@NonNull String name, @NonNull Context<?> ctx,
          @NonNull HelperRegistry registry);
}
