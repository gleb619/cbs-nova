package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Intercepts helper execution before the real helper runs.
 *
 * <p>
 * A non-empty return value short-circuits the helper and is returned to the caller. Empty means
 * "not intercepted — run the real helper".
 */
public interface HelperInterceptor {

  @NonNull
  Optional<Result<?>> intercept(@NonNull String helperName, @NonNull Context<?> ctx);
}
