package cbs.nova.dsl.exception;

import org.jspecify.annotations.NonNull;

/**
 * Aggregate exception thrown when one or more saga compensations fail. The first failing
 * compensation is the cause; any additional failures are attached via
 * {@link Throwable#addSuppressed(Throwable)}.
 */
public final class DslSagaCompensationException extends RuntimeException {

  public DslSagaCompensationException(@NonNull String message, @NonNull Throwable cause) {
    super(message, cause);
  }
}
