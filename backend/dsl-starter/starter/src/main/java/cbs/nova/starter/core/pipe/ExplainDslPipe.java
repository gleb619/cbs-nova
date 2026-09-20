package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.starter.config.properties.CbsNovaExplainProperties;
import cbs.nova.dsl.helper.NoopHelperInterceptor;
import cbs.nova.starter.core.stage.DispatchStage;
import cbs.nova.starter.core.stage.ExplainReportStage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class ExplainDslPipe implements DslExecutionPipe<ExplainReport> {

  private final CbsNovaExplainProperties explainProperties;

  @Override
  public @NonNull Result<ExplainReport> execute(@NonNull String name,
          @NonNull Context<?> ctx) {
    return DslExecutionPipeline.<ExplainReport>builder()
        .stage(new ExplainReportStage(explainProperties))
        .stage(DispatchStage.inline(NoopHelperInterceptor.INSTANCE))
        .build()
        .execute(name, ctx);
  }
}
