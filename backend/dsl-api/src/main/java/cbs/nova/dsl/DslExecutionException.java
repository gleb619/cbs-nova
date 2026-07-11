package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class DslExecutionException extends DslException {

  public DslExecutionException(
          @NonNull String runId, @NonNull String message, @Nullable Throwable cause) {
    super(runId, DslErrorCode.EXECUTION_FAILED, message, cause);
  }
}
