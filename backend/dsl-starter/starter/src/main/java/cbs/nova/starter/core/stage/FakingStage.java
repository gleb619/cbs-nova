package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Result;
import cbs.nova.starter.config.properties.CbsNovaFakesProperties;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.pipe.RunScopedFakeConfig;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class FakingStage implements DslPipeStage {

  private final CbsNovaFakesProperties properties;
  private final RunScopedFakeConfig runScopedFakeConfig;

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    if (properties.enabled()) {
      runScopedFakeConfig.register(context.runId(), properties.config());
    }
    try {
      return next.proceed(context);
    } finally {
      runScopedFakeConfig.remove(context.runId());
    }
  }
}
