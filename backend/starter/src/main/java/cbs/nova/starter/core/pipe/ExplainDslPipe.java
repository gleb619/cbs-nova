package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.config.CbsNovaPreviewProperties;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.core.stage.DispatchStage;
import cbs.nova.starter.core.stage.DryRunLogStage;
import cbs.nova.starter.core.stage.ExecutionTraceStage;
import cbs.nova.starter.core.stage.ExecutionTreeStage;
import cbs.nova.starter.core.stage.ExplainReportStage;
import cbs.nova.starter.core.stage.ExternalCallRecordingStage;
import cbs.nova.starter.core.stage.MetricsStage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class ExplainDslPipe implements DslExecutionPipe<ExplainReport> {

  private final ExternalCallRecorder recorder;
  private final ContextFactory contextFactory;
  private final DryRunLoggingContext dryRunLoggingContext;
  private final CbsNovaPreviewProperties previewProperties;
  private final ExecutionTraceCollector traceCollector;

  @Override
  public @NonNull Result<ExplainReport> execute(@NonNull String name,
          @NonNull Context<?> ctx) {
    return DslExecutionPipeline.<ExplainReport>builder()
            .stage(new ExplainReportStage())
            .stage(new MetricsStage())
            .stage(new ExecutionTreeStage(contextFactory,
                    previewProperties.callTree().maxDepth()))
            .stage(new DryRunLogStage(dryRunLoggingContext))
            .stage(new ExecutionTraceStage(traceCollector))
            .stage(new ExternalCallRecordingStage(recorder))
            .stage(new DispatchStage(contextFactory))
            .build()
            .execute(name, ctx);
  }
}
