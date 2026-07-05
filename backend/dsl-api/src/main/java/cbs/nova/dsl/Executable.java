package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public interface Executable<IN, OUT> {
  @NonNull
  Result<OUT> execute(@NonNull Context<IN> ctx);
}
