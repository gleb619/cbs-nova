package cbs.nova.starter.core.pipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.core.PreviewErrorHandler;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.exception.DslCapabilityDeniedException;
import cbs.nova.starter.security.ManifestObjectGuard;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * T552 preview-path enforcement: the {@link ManifestObjectGuardHelperInterceptor} short-circuits
 * denied helper calls with {@link DslCapabilityDeniedException}, allows allowlisted ones through to
 * the next interceptor, and {@link PreviewErrorHandler} maps the typed exception onto the unified
 * {@code CAPABILITY_DENIED} error envelope carrying piece/object identity.
 */
class ManifestObjectGuardHelperInterceptorTest {

  private static final String DEF = "OrderProcess";
  private static final String HELPER = "httpGet";
  private static final String RUN_ID = "run-1";
  private static final String CORRELATION_ID = "corr-123";

  private final ManifestObjectGuard guard = mock(ManifestObjectGuard.class);
  private final HelperInterceptor next = mock(HelperInterceptor.class);
  private final ManifestObjectGuardHelperInterceptor interceptor = new ManifestObjectGuardHelperInterceptor(
          guard, next);

  private static Context<?> previewContext() {
    return SimpleContext.builder("payload")
            .mode(ExecutionMode.PREVIEW)
            .runId(RUN_ID)
            .metadata(Map.of(
                    StarterConstants.DSL_DEFINITION_NAME_METADATA_KEY, DEF,
                    StarterConstants.CORRELATION_ID_METADATA_KEY, CORRELATION_ID))
            .build();
  }

  @Test
  void deniedHelperShortCircuitsWithTypedFailure() {
    ManifestObjectGuard.Denial denial = new ManifestObjectGuard.Denial(
            DEF, "piece-1", "helper", HELPER, "explicit deny piece matched");
    when(guard.check(eq(ExecutionMode.PREVIEW), eq(DEF), eq("helper"), eq(HELPER),
            eq(CORRELATION_ID))).thenReturn(Optional.of(denial));

    Optional<Result<?>> result = interceptor.intercept(HELPER, previewContext());

    assertThat(result).isPresent();
    assertThat(result.get().isSuccess()).isFalse();
    assertThat(result.get().cause()).isInstanceOf(DslCapabilityDeniedException.class);
    verify(next, never()).intercept(anyString(), any());
  }

  @Test
  void deniedFailureMapsToUnifiedCapabilityDeniedEnvelope() {
    DslCapabilityDeniedException ex = new DslCapabilityDeniedException(
            RUN_ID, "helper", HELPER, "piece-1", "explicit deny piece matched", CORRELATION_ID);

    ErrorResponse response = PreviewErrorHandler.from(ex, DEF);

    assertThat(response.code()).isEqualTo(StarterConstants.CAPABILITY_DENIED_CODE);
    assertThat(response.message()).contains(HELPER);
    assertThat(response.runId()).isEqualTo(RUN_ID);
    assertThat(response.correlationId()).isEqualTo(CORRELATION_ID);
    assertThat(response.context())
            .containsEntry("pieceId", "piece-1")
            .containsEntry("objectType", "helper")
            .containsEntry("objectName", HELPER)
            .containsEntry("reason", "explicit deny piece matched");
  }

  @Test
  void allowlistedHelperDelegatesToNextInterceptor() {
    when(guard.check(eq(ExecutionMode.PREVIEW), eq(DEF), eq("helper"), eq(HELPER),
            eq(CORRELATION_ID))).thenReturn(Optional.empty());
    Result<String> nextResult = Result.success("ok");
    when(next.intercept(eq(HELPER), any())).thenReturn(Optional.of(nextResult));

    Optional<Result<?>> result = interceptor.intercept(HELPER, previewContext());

    assertThat(result).containsSame(nextResult);
    verify(next).intercept(eq(HELPER), any());
  }

  @Test
  void definitionNameAndCorrelationIdDefaultToNullWhenMetadataAbsent() {
    when(guard.check(eq(ExecutionMode.PREVIEW), isNull(), eq("helper"), eq(HELPER),
            isNull())).thenReturn(Optional.empty());
    Context<?> bare = SimpleContext.builder("payload")
            .mode(ExecutionMode.PREVIEW)
            .runId(RUN_ID)
            .build();
    when(next.intercept(eq(HELPER), any())).thenReturn(Optional.empty());

    Optional<Result<?>> result = interceptor.intercept(HELPER, bare);

    assertThat(result).isEmpty();
    verify(guard).check(eq(ExecutionMode.PREVIEW), isNull(), eq("helper"), eq(HELPER),
            isNull());
  }
}
