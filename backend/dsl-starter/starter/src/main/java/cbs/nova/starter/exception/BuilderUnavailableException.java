package cbs.nova.starter.exception;

public class BuilderUnavailableException extends RuntimeException {

  public BuilderUnavailableException(String message) {
    super(message);
  }

  public BuilderUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

}
