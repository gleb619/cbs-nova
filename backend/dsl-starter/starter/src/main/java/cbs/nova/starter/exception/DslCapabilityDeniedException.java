package cbs.nova.starter.exception;

import cbs.nova.starter.core.StarterConstants;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Thrown when a DSL helper/process/function invocation is denied by the manifest-based object
 * guard. Carries enough context for the unified preview error envelope and production run failure
 * logs to report {@code CAPABILITY_DENIED} with the offending piece/object identity.
 */
@RequiredArgsConstructor
public final class DslCapabilityDeniedException extends RuntimeException {

  private final @Nullable String runId;
  private final @NonNull String objectType;
  private final @NonNull String objectName;
  private final @Nullable String pieceId;
  private final @NonNull String reason;
  private final @Nullable String correlationId;

  public @NonNull String runId() {
    return runId != null ? runId : "";
  }

  public @NonNull String objectType() {
    return objectType;
  }

  public @NonNull String objectName() {
    return objectName;
  }

  public @Nullable String pieceId() {
    return pieceId;
  }

  public @NonNull String reason() {
    return reason;
  }

  public @Nullable String correlationId() {
    return correlationId;
  }

  @Override
  public String getMessage() {
    return String.format(
            "Capability denied: %s (%s:%s) piece=%s reason=%s correlationId=%s",
            StarterConstants.CAPABILITY_DENIED_CODE, objectType, objectName,
            pieceId != null ? pieceId : "-", reason,
            correlationId != null ? correlationId : "-");
  }
}
