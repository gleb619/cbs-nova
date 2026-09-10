package cbs.nova.starter.exception;

import lombok.Getter;

/**
 * Thrown by the API-key admin endpoints when the caller references a key id that does not exist (or
 * has been revoked). Mapped to {@code 404 NOT_FOUND} by {@code DslExceptionHandler}.
 */
@Getter
public class ApiKeyNotFoundException extends RuntimeException {

  private final long keyId;

  public ApiKeyNotFoundException(long keyId) {
    super("API key not found: id=" + keyId);
    this.keyId = keyId;
  }
}
