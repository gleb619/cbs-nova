package cbs.nova.starter.exception;

import cbs.nova.starter.core.StarterConstants;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Thrown when a DSL helper/process/function invocation is denied by the manifest-based object
 * guard. Carries enough context for the unified preview error envelope and production run failure
 * logs to report {@code CAPABILITY_DENIED} with the offending piece/object identity.
 */
public final class DslCapabilityDeniedException extends RuntimeException {

  private final String runId;
  private final String objectType;
  private final String objectName;
  private final String pieceId;
  private final String reason;
  private final String correlationId;

  public DslCapabilityDeniedException(
          @Nullable String runId,
          @NonNull String objectType,
          @NonNull String objectName,
          @Nullable String pieceId,
          @NonNull String reason,
          @Nullable String correlationId) {
    super(String.format(
            "Capability denied: %s (%s:%s) piece=%s reason=%s correlationId=%s",
            StarterConstants.CAPABILITY_DENIED_CODE, objectType, objectName,
            pieceId != null ? pieceId : "-", reason,
            correlationId != null ? correlationId : "-"));
    this.runId = runId != null ? runId : "";
    this.objectType = objectType;
    this.objectName = objectName;
    this.pieceId = pieceId;
    this.reason = reason;
    this.correlationId = correlationId;
  }

  public @NonNull String runId() {
    return runId;
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
}
