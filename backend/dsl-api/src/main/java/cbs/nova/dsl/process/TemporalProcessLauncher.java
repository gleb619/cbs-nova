package cbs.nova.dsl.process;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface TemporalProcessLauncher {

  boolean canRun(@NonNull Context<?> ctx);

  @NonNull
  Result<?> launch(
          @NonNull String processName,
          @NonNull String taskQueue,
          @Nullable Class<?> inputType,
          @Nullable Class<?> outputType,
          @NonNull Context<?> ctx);
}
