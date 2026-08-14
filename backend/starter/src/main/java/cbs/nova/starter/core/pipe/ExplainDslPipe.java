package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.config.CbsNovaFakesProperties;
import cbs.nova.starter.config.CbsNovaPreviewProperties;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.core.stage.DispatchStage;
import cbs.nova.starter.core.stage.DryRunLogStage;
import cbs.nova.starter.core.stage.ExecutionTraceStage;
import cbs.nova.starter.core.stage.ExecutionTreeStage;
import cbs.nova.starter.core.stage.ExplainReportStage;
import cbs.nova.starter.core.stage.ExternalCallRecordingStage;
import cbs.nova.starter.core.stage.FakingStage;
import cbs.nova.starter.core.stage.MetricsStage;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import org.jspecify.annotations.NonNull;

public final class ExplainDslPipe implements DslExecutionPipe<ExplainReport> {

  private final ExternalCallRecorder recorder;
  private final ContextFactory contextFactory;
  private final DryRunLoggingContext dryRunLoggingContext;
  private final DryRunLogBufferRegistry bufferRegistry;
  private final int maxEventsPerRun;
  private final CbsNovaPreviewProperties previewProperties;
  private final CbsNovaFakesProperties fakesProperties;
  private final RunScopedFakeConfig runScopedFakeConfig;

  public ExplainDslPipe(
          ExternalCallRecorder recorder,
          ContextFactory contextFactory,
          DryRunLoggingContext dryRunLoggingContext,
          DryRunLogBufferRegistry bufferRegistry,
          int maxEventsPerRun,
          CbsNovaPreviewProperties previewProperties,
          CbsNovaFakesProperties fakesProperties,
          RunScopedFakeConfig runScopedFakeConfig) {
    this.recorder = recorder;
    this.contextFactory = contextFactory;
    this.dryRunLoggingContext = dryRunLoggingContext;
    this.bufferRegistry = bufferRegistry;
    this.maxEventsPerRun = maxEventsPerRun;
    this.previewProperties = previewProperties;
    this.fakesProperties = fakesProperties;
    this.runScopedFakeConfig = runScopedFakeConfig;
  }

  public ExplainDslPipe(
          ExternalCallRecorder recorder,
          ContextFactory contextFactory,
          DryRunLoggingContext dryRunLoggingContext,
          CbsNovaPreviewProperties previewProperties,
          CbsNovaFakesProperties fakesProperties,
          RunScopedFakeConfig runScopedFakeConfig) {
    this(recorder, contextFactory, dryRunLoggingContext, new DryRunLogBufferRegistry(),
            DryRunLogbackAppender.DEFAULT_MAX_EVENTS_PER_RUN, previewProperties,
            fakesProperties, runScopedFakeConfig);
  }

  @Override
  public @NonNull Result<ExplainReport> execute(@NonNull String name,
          @NonNull Context<?> ctx) {
    HelperInterceptor fakeInterceptor = new FakeHelperInterceptor(runScopedFakeConfig, recorder);
    return DslExecutionPipeline.<ExplainReport>builder()
            .stage(new ExplainReportStage())
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
