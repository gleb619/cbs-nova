package cbs.nova.starter.exception;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

/**
 * Thrown when a preview or explain dispatch call exceeds its configured execution timeout.
 */
@RequiredArgsConstructor
public final class PreviewTimeoutException extends RuntimeException {

  private final @NonNull String entityName;
  private final @NonNull Duration timeout;

  public @NonNull String entityName() {
    return entityName;
  }

  public @NonNull Duration timeout() {
    return timeout;
  }

  @Override
  public String getMessage() {
    return "Preview/explain execution of '" + entityName + "' exceeded timeout of "
            + timeout.toMillis() + " ms";
  }
}
