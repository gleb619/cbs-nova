package cbs.nova.dsl.exception;

import cbs.nova.dsl.DslErrorCode;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class DslCompensationException extends DslException {

  public DslCompensationException(
          @NonNull String runId, @NonNull String message, @Nullable Throwable cause) {
    super(runId, DslErrorCode.COMPENSATION_FAILED, message, cause);
  }
}
