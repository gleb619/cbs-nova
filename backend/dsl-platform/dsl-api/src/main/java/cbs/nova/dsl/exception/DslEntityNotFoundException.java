package cbs.nova.dsl.exception;

import cbs.nova.dsl.DslErrorCode;
import org.jspecify.annotations.NonNull;

public final class DslEntityNotFoundException extends DslException {

  public DslEntityNotFoundException(@NonNull String runId, @NonNull String message) {
    super(runId, DslErrorCode.ENTITY_NOT_FOUND, message);
  }
}
