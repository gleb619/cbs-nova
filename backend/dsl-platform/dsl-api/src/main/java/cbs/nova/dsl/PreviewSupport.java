package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

@FunctionalInterface
public interface PreviewSupport<IN, OUT> {

  @NonNull
  Result<OUT> preview(@NonNull Context<IN> ctx);

}
