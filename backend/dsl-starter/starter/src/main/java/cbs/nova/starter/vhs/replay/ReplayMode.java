package cbs.nova.starter.vhs.replay;

/**
 * Replay strategy for a tape.
 *
 * <ul>
 * <li>{@link #exact} — execute calls in tape order, preserving the original {@code relative_ms}
 * timing deltas. Used for deterministic bug reproduction.</li>
 * <li>{@link #load} — run N copies of the tape concurrently at a speed multiplier, bounded by a
 * concurrency cap. Used for load generation.</li>
 * </ul>
 */
public enum ReplayMode {
  exact, load
}
