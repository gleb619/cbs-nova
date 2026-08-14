package cbs.nova.dsl.process;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface DslTemporalProcess<T> {

  @Nullable
  Object execute(@NonNull DslTemporalProcessRequest<T> request);
}
