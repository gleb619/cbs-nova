package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.service.PreviewResultCache;
import cbs.nova.starter.config.CbsNovaFakesProperties;
import cbs.nova.starter.config.CbsNovaPreviewProperties;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.core.stage.DispatchStage;
import cbs.nova.starter.core.stage.DryRunLogStage;
import cbs.nova.starter.core.stage.ExecutionTraceStage;
import cbs.nova.starter.core.stage.ExecutionTreeStage;
import cbs.nova.starter.core.stage.ExternalCallRecordingStage;
import cbs.nova.starter.core.stage.FakingStage;
import cbs.nova.starter.core.stage.MetricsStage;
import cbs.nova.starter.core.stage.PreviewCacheStage;
import cbs.nova.starter.core.stage.PreviewReportStage;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class PreviewDslPipe implements DslExecutionPipe<PreviewReport> {

  private final ExternalCallRecorder recorder;
  private final ContextFactory contextFactory;
  private final DryRunLoggingContext dryRunLoggingContext;
  private final DryRunLogBufferRegistry bufferRegistry;
  private final int maxEventsPerRun;
  private final PreviewResultCache cache;
  private final CbsNovaPreviewProperties previewProperties;
  private final CbsNovaFakesProperties fakesProperties;
  private final RunScopedFakeConfig runScopedFakeConfig;


  @Override
  public @NonNull Result<PreviewReport> execute(@NonNull String name,
          @NonNull Context<?> ctx) {
    HelperInterceptor fakeInterceptor = new FakeHelperInterceptor(runScopedFakeConfig, recorder);
    return DslExecutionPipeline.<PreviewReport>builder()
            .stage(new PreviewCacheStage(cache))
            .stage(new PreviewReportStage())
            .stage(new MetricsStage())
            .stage(new ExecutionTreeStage(contextFactory,
                    previewProperties.callTree().maxDepth()))
            .stage(new DryRunLogStage(dryRunLoggingContext, bufferRegistry, maxEventsPerRun))
            .stage(new ExecutionTraceStage())
            .stage(new FakingStage(fakesProperties, runScopedFakeConfig))
            .stage(new ExternalCallRecordingStage(recorder))
            .stage(new DispatchStage(contextFactory, fakeInterceptor))
            .build()
            .execute(name, ctx);
  }
}
