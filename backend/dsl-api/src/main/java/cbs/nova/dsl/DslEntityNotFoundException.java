package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public final class DslEntityNotFoundException extends DslException {

  public DslEntityNotFoundException(@NonNull String runId, @NonNull String message) {
    super(runId, DslErrorCode.ENTITY_NOT_FOUND, message);
  }
}
