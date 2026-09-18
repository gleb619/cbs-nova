package cbs.nova.starter.core.pipe;

import static cbs.nova.starter.core.StarterConstants.CORRELATION_ID_METADATA_KEY;
import static cbs.nova.starter.core.StarterConstants.DSL_DEFINITION_NAME_METADATA_KEY;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.starter.exception.DslCapabilityDeniedException;
import cbs.nova.starter.security.ManifestObjectGuard;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

/**
 * Helper interceptor that consults {@link ManifestObjectGuard} before delegating to the next
 * interceptor (usually {@link FakeHelperInterceptor}). A denial short-circuits the helper call
 * with a typed {@link DslCapabilityDeniedException} wrapped in a {@link Result#failure}; an allow
 * passes through unchanged.
 *
 * <p>
 * The executing definition name is read from context metadata
 * ({@code cbs.nova.dsl.definitionName}); the pipeline injects it before dispatch so the guard can
 * evaluate object pieces scoped to a definition.
 */
@RequiredArgsConstructor
public final class ManifestObjectGuardHelperInterceptor implements HelperInterceptor {

  private final ManifestObjectGuard guard;
  private final HelperInterceptor next;

  @Override
  public @NonNull Optional<Result<?>> intercept(@NonNull String helperName,
          @NonNull Context<?> ctx) {
    Optional<ManifestObjectGuard.Denial> denial = guard.check(
            ctx.mode(),
            definitionName(ctx),
            "helper",
            helperName,
            correlationId(ctx));

    if (denial.isPresent()) {
      ManifestObjectGuard.Denial d = denial.get();
      DslCapabilityDeniedException ex = new DslCapabilityDeniedException(
              ctx.runId(), d.objectType(), d.objectName(), d.pieceId(), d.reason(),
              correlationId(ctx));
      return Optional.of(Result.failure(ex));
    }

    return next.intercept(helperName, ctx);
  }

  private String definitionName(Context<?> ctx) {
    Object value = ctx.metadata().get(DSL_DEFINITION_NAME_METADATA_KEY);
    return value instanceof String s && !s.isBlank() ? s : null;
  }

  private String correlationId(Context<?> ctx) {
    Object value = ctx.metadata().get(CORRELATION_ID_METADATA_KEY);
    return value instanceof String s && !s.isBlank() ? s : null;
  }
}
