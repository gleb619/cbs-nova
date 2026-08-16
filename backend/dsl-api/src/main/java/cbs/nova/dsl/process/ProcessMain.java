package cbs.nova.dsl.process;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import org.jspecify.annotations.NonNull;

@FunctionalInterface
public interface ProcessMain {

  @NonNull
  Result<?> apply(@NonNull Context<?> ctx);
}
