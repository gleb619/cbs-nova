package cbs.nova.dsl.security;

import cbs.nova.dsl.ExecutionMode;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Pluggable guard consulted by the DSL runtime before invoking a helper, function, or process.
 * Implementations are typically provided by the host runtime (e.g. the Spring Boot starter) and
 * back onto a manifest-based allowlist. The default is a no-op so the platform runtime keeps
 * working without a host guard.
 */
public interface ObjectGuard {

  /** No-op guard: every invocation is allowed. */
  ObjectGuard NO_OP = new ObjectGuard() {
    @Override
    public @NonNull Optional<Denial> check(
            @NonNull ExecutionMode mode,
            @Nullable String definitionName,
            @NonNull String objectType,
            @NonNull String objectName,
            @Nullable String correlationId) {
      return Optional.empty();
    }

    @Override
    public boolean active() {
      return false;
    }
  };


  /**
   * Returns {@code true} when the guard is actively enforcing (i.e. the feature flag is on).
   * The no-op default returns {@code false}.
   */
  default boolean active() {
    return false;
  }


  /**
   * Evaluates whether the invocation of {@code objectType:objectName} on behalf of
   * {@code definitionName} is allowed.
   *
   * @return {@link Optional#empty()} when the invocation is allowed, or a {@link Denial} carrying
   *         context when it must be rejected.
   */
  @NonNull
  Optional<Denial> check(
          @NonNull ExecutionMode mode,
          @Nullable String definitionName,
          @NonNull String objectType,
          @NonNull String objectName,
          @Nullable String correlationId);

  /**
   * Rejection context returned by {@link #check}.
   */
  record Denial(
          @Nullable String definitionName,
          @Nullable String pieceId,
          @NonNull String objectType,
          @NonNull String objectName,
          @NonNull String reason) {
  }
}
