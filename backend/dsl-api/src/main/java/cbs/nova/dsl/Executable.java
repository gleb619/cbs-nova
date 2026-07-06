package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.List;

public interface Executable<IN, OUT> {
  @NonNull
  default Result<OUT> preview(@NonNull Context<IN> ctx) {
    return execute(ctx);
  }

  @NonNull
  Result<OUT> execute(@NonNull Context<IN> ctx);

  @NonNull
  default ExecutableDescriptor describe() {
    return new ExecutableDescriptor(null, null, null, null, true, "delegates to execute",
            List.of());
  }
}
