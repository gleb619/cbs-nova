package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

/**
 * Functional interface supplied by generated Temporal process workflows to the
 * {@link GlobalManager} saga runner. It represents the main process logic that should be executed
 * under compensation.
 */
@FunctionalInterface
public interface ProcessMain {

  @NonNull
  Result<?> apply(@NonNull Context<?> ctx);
}
