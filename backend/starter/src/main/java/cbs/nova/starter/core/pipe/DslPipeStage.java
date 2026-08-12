package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Result;
import org.jspecify.annotations.NonNull;

@FunctionalInterface
public interface DslPipeStage {

  @NonNull
  Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next);

  interface Next {
    @NonNull
    Result<?> proceed(@NonNull DslPipeContext context);
  }
}
