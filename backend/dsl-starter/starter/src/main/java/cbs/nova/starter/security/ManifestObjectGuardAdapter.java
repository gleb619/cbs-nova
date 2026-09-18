package cbs.nova.starter.security;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.security.ObjectGuard;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Bridges the platform {@link ObjectGuard} seam to the starter's
 * {@link ManifestObjectGuard}. Registered into {@link cbs.nova.dsl.config.DslConfig} during
 * auto-configuration so every helper/function invocation in the DSL runtime consults the same
 * manifest-based guard as the preview pipes.
 */
public final class ManifestObjectGuardAdapter implements ObjectGuard {

  private final ManifestObjectGuard delegate;

  public ManifestObjectGuardAdapter(@NonNull ManifestObjectGuard delegate) {
    this.delegate = delegate;
  }

  @Override
  public @NonNull Optional<Denial> check(
          @NonNull ExecutionMode mode,
          @Nullable String definitionName,
          @NonNull String objectType,
          @NonNull String objectName,
          @Nullable String correlationId) {
    Optional<ManifestObjectGuard.Denial> denial = delegate.check(
            mode, definitionName, objectType, objectName, correlationId);
    return denial.map(d -> new Denial(
            d.definitionName(), d.pieceId(), d.objectType(), d.objectName(), d.reason()));
  }

  @Override
  public boolean active() {
    return delegate.isActive();
  }
}
