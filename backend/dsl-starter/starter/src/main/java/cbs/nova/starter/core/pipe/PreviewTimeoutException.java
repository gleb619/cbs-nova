package cbs.nova.starter.core.pipe;

import java.time.Duration;
import org.jspecify.annotations.NonNull;

/**
 * Thrown when a preview or explain dispatch call exceeds its configured execution timeout.
 */
public final class PreviewTimeoutException extends RuntimeException {

  private final String entityName;
  private final Duration timeout;

  public PreviewTimeoutException(@NonNull String entityName, @NonNull Duration timeout) {
    super("Preview/explain execution of '" + entityName + "' exceeded timeout of "
            + timeout.toMillis() + " ms");
    this.entityName = entityName;
    this.timeout = timeout;
  }

  public @NonNull String entityName() {
    return entityName;
  }

  public @NonNull Duration timeout() {
    return timeout;
  }
}
