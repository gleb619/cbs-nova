package cbs.nova.dsl.logging;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface DryRunLoggingContext {

  String RUN_ID_HEADER = "x-cbs-nova-dry-run-run-id";

  default void runWithRunId(@NonNull String runId, @NonNull Runnable action) {
    setRunId(runId);
    try {
      action.run();
    } finally {
      clearRunId();
    }
  }

  void setRunId(@Nullable String runId);

  void clearRunId();

  @Nullable
  String currentRunId();
}
