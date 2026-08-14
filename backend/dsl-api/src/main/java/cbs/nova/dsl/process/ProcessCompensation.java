package cbs.nova.dsl.process;

import cbs.nova.dsl.Context;
import org.jspecify.annotations.NonNull;

/**
 * Functional interface supplied by generated Temporal process workflows to the
 * {@link GlobalManager} saga runner. It represents the process-level compensation logic invoked
 * when the main process logic fails.
 */
@FunctionalInterface
public interface ProcessCompensation {

  void accept(@NonNull Context<?> ctx, @NonNull Throwable error);
}
