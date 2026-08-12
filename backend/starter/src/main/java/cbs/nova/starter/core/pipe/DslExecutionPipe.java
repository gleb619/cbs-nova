package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import org.jspecify.annotations.NonNull;

public interface DslExecutionPipe<R> {

  @NonNull
  Result<R> execute(@NonNull String name, @NonNull Context<?> ctx);
}
