package cbs.nova.dsl.process;

import org.jspecify.annotations.NonNull;

/**
 * Raised in preview mode when a seeded payload cannot be converted to the signal's declared type.
 */
public class SignalPayloadTypeMismatchException extends RuntimeException {

  private final String signalName;

  public SignalPayloadTypeMismatchException(@NonNull String signalName,
          @NonNull String actualType, @NonNull String expectedType) {
    super("Seeded payload for signal '" + signalName + "' is a " + actualType
            + " and cannot be converted to the declared type " + expectedType);
    this.signalName = signalName;
  }

  public @NonNull String signalName() {
    return signalName;
  }
}
