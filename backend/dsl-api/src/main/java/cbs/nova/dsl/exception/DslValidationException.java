package cbs.nova.dsl.exception;

import cbs.nova.dsl.DslErrorCode;
import org.jspecify.annotations.NonNull;

public final class DslValidationException extends DslException {

  public DslValidationException(@NonNull String runId, @NonNull String message) {
    super(runId, DslErrorCode.VALIDATION_FAILED, message);
  }
}
