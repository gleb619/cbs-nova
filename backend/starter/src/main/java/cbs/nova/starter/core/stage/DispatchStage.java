package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslSaga;
import cbs.nova.dsl.ExecutionListener;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public final class DispatchStage implements DslPipeStage {

  private final ContextFactory contextFactory;

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    Context<?> modeCtx = buildModeContext(context);
    Result<?> result = dispatch(context.getName(), modeCtx);
    context.setAttribute("dslResult", result);
    return next.proceed(context);
  }

  private @NonNull Context<?> buildModeContext(@NonNull DslPipeContext context) {
    Context<?> original = context.getDslContext();
    Context<?> modeCtx = contextFactory.of(
            original.body(),
            original.metadata(),
            context.getMode(),
            context.getRunId(),
            original.transactionRouting());
    modeCtx = withExistingListener(modeCtx, original.executionListener());
    modeCtx = withExistingSaga(modeCtx, original.saga());
    return modeCtx;
  }

  private @NonNull Context<?> withExistingListener(@NonNull Context<?> ctx,
          @Nullable ExecutionListener listener) {
    return listener != null ? ctx.withExecutionListener(listener) : ctx;
  }

  private @NonNull Context<?> withExistingSaga(@NonNull Context<?> ctx, @Nullable DslSaga saga) {
    return saga != null ? ctx.withSaga(saga) : ctx;
  }

  private @NonNull Result<?> dispatch(@NonNull String name, @NonNull Context<?> ctx) {
    GlobalManager gm = GlobalManager.globalManager();
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
