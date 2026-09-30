package cbs.nova.dsl.process;

import org.jspecify.annotations.NonNull;

/**
 * Raised in preview / hierarchy / explain mode when a process awaits a signal that was not seeded
 * on the request timeline.
 *
 * <p>
 * Preview never waits on wall-clock time, so an unseeded await resolves immediately with this
 * failure instead of hanging or falling back to the generic "missing SignalAwaiter"
 * {@link IllegalStateException}.
 */
public class SignalNotSeededException extends RuntimeException {

  private final String signalName;

  public SignalNotSeededException(@NonNull String signalName) {
    super("Signal '" + signalName + "' was not seeded on the preview signal timeline");
    this.signalName = signalName;
  }

  public @NonNull String signalName() {
    return signalName;
  }
}
