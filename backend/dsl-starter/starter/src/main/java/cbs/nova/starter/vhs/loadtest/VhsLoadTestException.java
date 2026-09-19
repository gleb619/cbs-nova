package cbs.nova.starter.vhs.loadtest;

/**
 * Raised for load-test orchestration failures: no tape files resolved, invalid parameters, or
 * replay errors during a load-test run.
 */
public final class VhsLoadTestException extends RuntimeException {

  public VhsLoadTestException(String message) {
    super(message);
  }

  public VhsLoadTestException(String message, Throwable cause) {
    super(message, cause);
  }
}
