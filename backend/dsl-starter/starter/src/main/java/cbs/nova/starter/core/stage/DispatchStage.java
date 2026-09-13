package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslSaga;
import cbs.nova.dsl.listener.ExecutionTraceCollector;
import cbs.nova.dsl.listener.ExecutionListener;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.pipe.PreviewTimeoutException;
import io.micrometer.core.instrument.MeterRegistry;
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

  private final ContextFactory contextFactory;
  private final HelperInterceptor helperInterceptor;
  private final Duration timeout;
  private final ExecutorService executor;
  private final MeterRegistry meterRegistry;
  private final DryRunLoggingContext dryRunLoggingContext;

  public static DispatchStage inline(@NonNull ContextFactory contextFactory,
          @NonNull HelperInterceptor helperInterceptor) {
    return new DispatchStage(contextFactory, helperInterceptor, null, null, null, null);
  }

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    Context<?> modeCtx = buildModeContext(context);
    GlobalManager gm = GlobalManager.globalManager();
    Result<?> result = dispatchWithOptionalTimeout(context.name(), modeCtx, gm);
    context.setAttribute("dslResult", result);
    return next.proceed(context);
  }

  private @NonNull Context<?> buildModeContext(@NonNull DslPipeContext context) {
    Context<?> original = context.dslContext();
    Context<?> modeCtx = contextFactory.of(
            original.body(),
            original.metadata(),
            context.mode(),
            context.runId(),
            original.transactionRouting());
    modeCtx = withExistingListener(modeCtx, original.executionListener());
    modeCtx = withExistingSaga(modeCtx, original.saga());
    modeCtx = withExistingCollector(modeCtx, original.executionTraceCollector());
    modeCtx = withHelperInterceptor(modeCtx);
    return modeCtx;
  }

  private @NonNull Context<?> withExistingListener(@NonNull Context<?> ctx,
          @Nullable ExecutionListener listener) {
    return listener != null ? ctx.withExecutionListener(listener) : ctx;
  }

  private @NonNull Context<?> withExistingSaga(@NonNull Context<?> ctx, @Nullable DslSaga saga) {
    return saga != null ? ctx.withSaga(saga) : ctx;
  }

  private @NonNull Context<?> withExistingCollector(@NonNull Context<?> ctx,
          @Nullable ExecutionTraceCollector collector) {
    return collector != null ? ctx.withExecutionTraceCollector(collector) : ctx;
  }

  private @NonNull Context<?> withHelperInterceptor(@NonNull Context<?> ctx) {
    return helperInterceptor != null ? ctx.withHelperInterceptor(helperInterceptor) : ctx;
  }

  private @NonNull Result<?> dispatchWithOptionalTimeout(@NonNull String name,
          @NonNull Context<?> ctx, @NonNull GlobalManager gm) {
    if (executor == null || timeout == null || timeout.isNegative() || timeout.isZero()) {
      return dispatch(name, ctx, gm);
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
      return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      if (meterRegistry != null) {
        meterRegistry.counter("dsl.preview.timeout").increment();
      }
      return Result.failure(new PreviewTimeoutException(name, timeout));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.failure(e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
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
}
