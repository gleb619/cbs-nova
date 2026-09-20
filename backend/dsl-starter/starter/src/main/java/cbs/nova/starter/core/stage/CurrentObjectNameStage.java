package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import org.jspecify.annotations.NonNull;

/**
 * Stamps the currently active DSL object name (process/transaction/function/helper) into the
 * context metadata under {@link Constants#CURRENT_OBJECT_NAME} so downstream stages, dispatched
 * code, and nested executions know which object they are working on.
 */
public final class CurrentObjectNameStage implements DslPipeStage {

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    Context<?> ctx = context.dslContext()
            .withMetadata(Constants.CURRENT_OBJECT_NAME, context.name());
    return next.proceed(context.withDslContext(ctx));
  }
}
