package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class DslException extends RuntimeException {
  private final String runId;
  private final String exceptionId;
  private final DslErrorCode code;

  public DslException(@NonNull String runId, @NonNull DslErrorCode code, @NonNull String message) {
    this(runId, code, message, null);
  }

  public DslException(
          @NonNull String runId,
          @NonNull DslErrorCode code,
          @NonNull String message,
          @Nullable Throwable cause) {
    super(message, cause);
    this.runId = runId;
    this.code = code;
    this.exceptionId = runId + ":ex:" + UUID.randomUUID();
  }

  public final @NonNull String runId() {
    return runId;
  }

  public final @NonNull String exceptionId() {
    return exceptionId;
  }

  public final @NonNull DslErrorCode code() {
    return code;
  }
}
