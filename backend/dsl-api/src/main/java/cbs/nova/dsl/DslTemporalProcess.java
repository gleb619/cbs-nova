package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface DslTemporalProcess<T> {

  @Nullable
  Object execute(@NonNull DslTemporalProcessRequest<T> request);
}
