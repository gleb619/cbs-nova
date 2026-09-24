package cbs.nova.starter.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Thrown when an incoming run/preview request body exceeds the configured
 * {@code cbs.runs.max-input-bytes} limit.
 */
@Getter
@RequiredArgsConstructor
public class DslPayloadTooLargeException extends RuntimeException {

  private final long limit;
  private final long actualBytes;
  private final String entityName;

  @Override
  public String getMessage() {
    return "Request body for '%s' exceeds maximum input size (limit %d bytes, actual %d bytes)"
            .formatted(entityName, limit, actualBytes);
  }
}
