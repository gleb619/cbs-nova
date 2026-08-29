package cbs.nova.starter.exception;

import lombok.Getter;

/**
 * Thrown when an incoming run/preview request body exceeds the configured
 * {@code cbs.runs.max-input-bytes} limit.
 */
@Getter
public class DslPayloadTooLargeException extends RuntimeException {

  private final long limit;
  private final long actualBytes;
  private final String entityName;

  public DslPayloadTooLargeException(long limit, long actualBytes, String entityName) {
    super("Request body for '%s' exceeds maximum input size (limit %d bytes, actual %d bytes)"
            .formatted(entityName, limit, actualBytes));
    this.limit = limit;
    this.actualBytes = actualBytes;
    this.entityName = entityName;
  }
}
