package cbs.nova.starter.vhs.replay.fake;

import cbs.nova.starter.vhs.TapeEvent;
import org.jspecify.annotations.NonNull;

/**
 * Pluggable transformation layer between {@code VhsTapeReader} and {@code VhsCallDriver}.
 *
 * <p>
 * Given a {@code call_start} tape event, returns the event with its {@code input} tree rewritten so
 * that real identifiers, secrets, and references are replaced with synthetic equivalents before the
 * call is dispatched to the replay target. The mapping from {@code real value → synthetic
 * value} is held in-memory per replay session so that two calls referencing the same real id
 * resolve to the same synthetic value (the second call's input sees the first call's synthetic
 * output as if it were real input — see {@code SyntheticIdReplayFaker}).
 *
 * <p>
 * <b>Distinct from T556's record-time scrubbing.</b> T556 protects the tape at rest; this layer
 * protects the replay target from real production data at run time.
 */
public interface VhsReplayFaker {

  /**
   * Apply faking to {@code event}'s {@code input} tree.
   *
   * @param event
   *          a {@code call_start} tape event (never {@code null})
   * @return a new event with faked input, or {@code event} unchanged when faking is disabled or no
   *         rule matches
   */
  @NonNull
  TapeEvent fake(@NonNull TapeEvent event);

  /**
   * Whether this faker is active. The replay engine treats {@code false} as a no-op pass-through.
   */
  boolean enabled();

  static VhsReplayFaker noop() {
    return new VhsReplayFaker() {
      @Override
      public TapeEvent fake(TapeEvent event) {
        return event;
      }

      @Override
      public boolean enabled() {
        return false;
      }
    };
  }
}
