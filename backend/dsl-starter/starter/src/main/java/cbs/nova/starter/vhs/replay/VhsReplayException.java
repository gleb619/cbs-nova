package cbs.nova.starter.vhs.replay;

/**
 * Raised for any replay-related failure: malformed or truncated tapes, unsupported tape/schema
 * versions, illegal replay settings, or a rejected replay target.
 *
 * <p>
 * This exception is always loud: a tape that cannot be fully validated never executes a single
 * call. Partial replays are never started silently.
 */
public final class VhsReplayException extends RuntimeException {

  public VhsReplayException(String message) {
    super(message);
  }

  public VhsReplayException(String message, Throwable cause) {
    super(message, cause);
  }
}
