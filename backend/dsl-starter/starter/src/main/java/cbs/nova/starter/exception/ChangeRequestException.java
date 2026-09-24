package cbs.nova.starter.exception;

import lombok.RequiredArgsConstructor;

/**
 * Domain failure of a change-request operation (T568). The {@link #code()} is an
 * {@link cbs.nova.dsl.model.ErrorResponse}-style code the handler maps to an HTTP status:
 * {@code NOT_FOUND} → 404, {@code FORBIDDEN} → 403, {@code CONFLICT} → 409.
 */
@RequiredArgsConstructor
public class ChangeRequestException extends RuntimeException {

  private final String code;
  private final String message;

  public String code() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
