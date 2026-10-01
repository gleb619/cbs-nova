package cbs.nova.starter.core.stage;

import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.exception.PreviewTimeoutException;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes the DSL entity for the current pipe run.
 *
 * <p>
 * The {@code helperInterceptor} is applied by setting it on the per-execution {@link Context} via
 * {@link Context#withHelperInterceptor}; the dispatcher reads it from the context (no ThreadLocal,
 * no GlobalManager mutation).
 *
 * <p>
 * When a non-zero timeout and executor are configured, only the actual dispatch call runs on a
 * dedicated worker thread. The helper interceptor travels with the context the worker receives, so
 * faked helpers still fire on the worker thread.
 *
 * <p>
 * Cancellation is cooperative: {@code Future.cancel(true)} sends an interrupt, which ends
 * interruptible waits (e.g. {@code Thread.sleep}), but a pure CPU spin loop keeps its worker thread
 * until it exits. The JVM provides no safe thread kill, so the pool is bounded and named for
 * diagnosability.
 *
 * <p>
 * Dispatch workers do not inherit per-request MDC / log correlation from the request thread; if
 * logs produced inside the dispatched DSL are required to carry the run id, propagate the MDC
 * explicitly (e.g. via a {@code TaskDecorator}).
 */
@RequiredArgsConstructor
public final class DispatchStage implements DslPipeStage {

  // T746: tracer is intentionally optional — when null, this stage is telemetry-silent (matches
  // the existing OpenTelemetry.noop() behavior). Production wires a real Tracer bean.
  private final HelperInterceptor helperInterceptor;
  private final Duration timeout;
  private final ExecutorService executor;
  private final MeterRegistry meterRegistry;
  private final DryRunLoggingContext dryRunLoggingContext;
  private final @Nullable Tracer tracer;

  public static DispatchStage inline(
          @NonNull HelperInterceptor helperInterceptor) {
    return new DispatchStage(helperInterceptor, null, null, null, null, null);
  }

  /**
   * Legacy constructor preserved for callers / tests that do not pass a tracer. Telemetry is
   * disabled when the tracer is null.
   */
  public DispatchStage(HelperInterceptor helperInterceptor, Duration timeout,
          ExecutorService executor, MeterRegistry meterRegistry,
          DryRunLoggingContext dryRunLoggingContext) {
    this(helperInterceptor, timeout, executor, meterRegistry, dryRunLoggingContext, null);
  }

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    // T746: span around preview/explain/hierarchy/run dispatch. The plan scopes this to the
    // "preview dispatch" entry point — DispatchStage is the single dispatch entry shared by all
    // four pipes; tagging by mode keeps Jaeger queries clean without per-pipe stage wrappers.
    Span span = tracer != null
            ? tracer.spanBuilder("dsl.preview.dispatch")
                    .setAttribute("definition", context.name())
                    .setAttribute("mode", context.mode().name())
                    .setAttribute("runId",
                            context.runId() != null ? context.runId() : "")
                    .startSpan()
            : null;
    try (Scope ignored = span != null ? span.makeCurrent() : null) {
      Context<?> modeCtx = buildModeContext(context);
      GlobalManager gm = GlobalManager.globalManager();
      Result<?> result = dispatchWithOptionalTimeout(context.name(), modeCtx, gm);
      context.setAttribute("dslResult", result);
      tagOutcome(span, result);
      return next.proceed(context);
    } catch (RuntimeException e) {
      if (span != null) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR, e.getMessage());
        span.setAttribute("outcome", "error");
      }
      throw e;
    } finally {
      if (span != null) {
        span.end();
      }
    }
  }

  private static void tagOutcome(@Nullable Span span, @NonNull Result<?> result) {
    if (span != null) {
      span.setAttribute("outcome", result.isSuccess() ? "success" : "error");
    }
  }

  private @NonNull Context<?> buildModeContext(@NonNull DslPipeContext context) {
    Context<?> original = context.dslContext();
    Context<?> modeCtx = SimpleContext.builder().body(original.body()).metadata(original.metadata())
            .mode(context.mode()).runId(context.runId())
            .transactionRouting(original.transactionRouting()).build();
    modeCtx = modeCtx.withExecutionListener(original.executionListener());
    modeCtx = modeCtx.withSaga(original.saga());
    modeCtx = modeCtx.withExecutionTraceCollector(original.executionTraceCollector());
    modeCtx = modeCtx.withHelperInterceptor(helperInterceptor);
    return modeCtx;
  }

  private @NonNull Result<?> dispatchWithOptionalTimeout(@NonNull String name,
          @NonNull Context<?> ctx, @NonNull GlobalManager gm) {
    if (executor == null || timeout == null || timeout.isNegative() || timeout.isZero()) {
      Result<?> result = dispatch(name, ctx, gm);
      recordPreviewOutcome(result);
      return result;
    }

    String runId = dryRunLoggingContext != null ? dryRunLoggingContext.currentRunId() : null;
    Future<Result<?>> future = executor.submit(() -> {
      if (runId != null) {
        dryRunLoggingContext.setRunId(runId);
      }
      try {
        return dispatch(name, ctx, gm);
      } finally {
        if (runId != null) {
          dryRunLoggingContext.clearRunId();
        }
      }
    });
    try {
      Result<?> result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      recordPreviewOutcome(result);
      return result;
    } catch (TimeoutException e) {
      future.cancel(true);
      if (meterRegistry != null) {
        meterRegistry.counter("dsl.preview.timeout").increment();
      }
      recordPreviewOutcome("timeout");
      return Result.failure(new PreviewTimeoutException(name, timeout));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      recordPreviewOutcome("error");
      return Result.failure(e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      recordPreviewOutcome("error");
      return Result.failure(cause);
    }
  }

  private @NonNull Result<?> dispatch(@NonNull String name, @NonNull Context<?> ctx,
          @NonNull GlobalManager gm) {
    if (gm.hasProcess(name)) {
      return gm.runProcess(name, ctx);
    }
    if (gm.hasTransaction(name)) {
      return gm.runTransaction(name, ctx);
    }
    if (gm.hasHelper(name)) {
      return gm.runHelper(name, ctx);
    }
    return Result.failure(new IllegalArgumentException("No DSL entity registered: " + name));
  }

  private void recordPreviewOutcome(@NonNull Result<?> result) {
    recordPreviewOutcome(result.isSuccess() ? "success" : "error");
  }

  private void recordPreviewOutcome(String outcome) {
    if (meterRegistry != null) {
      meterRegistry.counter(StarterConstants.PREVIEW_OUTCOME_COUNTER, "outcome", outcome)
              .increment();
    }
  }
}
