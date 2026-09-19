package cbs.nova.starter.approval;

/**
 * Domain failure of a change-request operation (T568). The {@link #code()} is an
 * {@link cbs.nova.dsl.model.ErrorResponse}-style code the handler maps to an HTTP status:
 * {@code NOT_FOUND} → 404, {@code FORBIDDEN} → 403, {@code CONFLICT} → 409.
 */
public class ChangeRequestException extends RuntimeException {

  private final String code;

  public ChangeRequestException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
