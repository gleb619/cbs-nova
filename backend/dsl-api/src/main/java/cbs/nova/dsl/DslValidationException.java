package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public final class DslValidationException extends DslException {
  public DslValidationException(@NonNull String runId, @NonNull String message) {
    super(runId, DslErrorCode.VALIDATION_FAILED, message);
  }
}
