package cbs.nova.starter.vhs.replay;

import cbs.nova.starter.vhs.TapeEvent;
import org.jspecify.annotations.Nullable;

/**
 * Dispatches a {@code call_start} tape event to the replay target environment.
 *
 * <p>
 * Implementations translate the event's {@code call_metadata} ({@code type}, {@code target},
 * {@code operation}) plus its {@code input} payload into a call against the target. The result is
 * recorded in the replay report; a non-successful result aborts that tape's replay.
 *
 * <p>
 * <b>Operational safety:</b> the engine replays real, previously recorded actions. Drivers must
 * therefore only be created through {@link VhsCallDrivers}, which enforces the two-key production
 * opt-in guard. Do not instantiate network-backed drivers directly for production-like targets.
 */
public interface VhsCallDriver {

  /**
   * Execute one recorded call.
   *
   * @param callStartEvent
   *          the {@code call_start} tape event (never {@code null})
   * @return the call outcome; never {@code null}
   */
  CallResult execute(TapeEvent callStartEvent);

  /**
   * Outcome of a single replayed call.
   *
   * @param success
   *          whether the target accepted the call
   * @param output
   *          the output returned by the target, if any
   * @param error
   *          human-readable failure reason when {@code success} is {@code false}
   */
  record CallResult(boolean success, @Nullable Object output, @Nullable String error) {

    public static CallResult success(@Nullable Object output) {
      return new CallResult(true, output, null);
    }

    public static CallResult failure(String error) {
      return new CallResult(false, null, error);
    }
  }
}
