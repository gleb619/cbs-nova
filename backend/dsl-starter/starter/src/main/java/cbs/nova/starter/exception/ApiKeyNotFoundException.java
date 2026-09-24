package cbs.nova.starter.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Thrown by the API-key admin endpoints when the caller references a key id that does not exist (or
 * has been revoked). Mapped to {@code 404 NOT_FOUND} by {@code DslExceptionHandler}.
 */
@Getter
@RequiredArgsConstructor
public class ApiKeyNotFoundException extends RuntimeException {

  private final long keyId;

  @Override
  public String getMessage() {
    return "API key not found: id=" + keyId;
  }

}
