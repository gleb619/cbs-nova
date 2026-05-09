package cbs.dsl.exception;

public class DslException extends RuntimeException {

  public DslException(String message, Object... args) {
    super(message.formatted(args));
  }

  public DslException(Throwable cause, String message, Object... args) {
    super(message.formatted(args), cause);
  }
}
