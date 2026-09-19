package cbs.nova.starter.notification;

import org.jspecify.annotations.Nullable;

/**
 * Result of one sink delivery attempt. {@code outcome} is one of {@code success}, {@code failure},
 * {@code noop}, {@code skipped}, {@code rejected}; {@code detail} carries the HTTP status or error
 * message when there is anything worth recording.
 */
public record FiringOutcome(String outcome, @Nullable String detail, long durationMs) {

  public static FiringOutcome success(@Nullable String detail, long durationMs) {
    return new FiringOutcome("success", detail, durationMs);
  }

  public static FiringOutcome failure(@Nullable String detail, long durationMs) {
    return new FiringOutcome("failure", detail, durationMs);
  }

  public static FiringOutcome noop(@Nullable String detail, long durationMs) {
    return new FiringOutcome("noop", detail, durationMs);
  }

  public static FiringOutcome skipped(@Nullable String detail) {
    return new FiringOutcome("skipped", detail, 0L);
  }

  public static FiringOutcome rejected(@Nullable String detail, long durationMs) {
    return new FiringOutcome("rejected", detail, durationMs);
  }
}
