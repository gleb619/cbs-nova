package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.dsl.model.HierarchyAccumulator;
import cbs.nova.dsl.model.HierarchyReport;
import cbs.nova.starter.config.properties.CbsNovaFakesProperties;
import cbs.nova.starter.config.properties.CbsNovaPreviewProperties;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.core.stage.DispatchStage;
import cbs.nova.starter.core.stage.DryRunLogStage;
import cbs.nova.starter.core.stage.ExecutionTraceStage;
import cbs.nova.starter.core.stage.ExecutionTreeStage;
import cbs.nova.starter.core.stage.ExternalCallRecordingStage;
import cbs.nova.starter.core.stage.FakingStage;
import cbs.nova.starter.core.stage.HierarchyReportStage;
import cbs.nova.starter.core.stage.MetricsStage;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.reporting.HierarchyDiagramRenderer;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class HierarchyDslPipe implements DslExecutionPipe<HierarchyReport> {

  private final ExternalCallRecorder recorder;
  private final DryRunLoggingContext dryRunLoggingContext;
  private final DryRunLogBufferRegistry bufferRegistry;
  private final int maxEventsPerRun;
  private final CbsNovaPreviewProperties previewProperties;
  private final CbsNovaFakesProperties fakesProperties;
  private final RunScopedFakeConfig runScopedFakeConfig;
  private final MeterRegistry meterRegistry;
  private final HierarchyDiagramRenderer diagramRenderer;
  private final ExecutorService executor;

  @Override
  public @NonNull Result<HierarchyReport> execute(@NonNull String name,
          @NonNull Context<?> ctx) {
    HelperInterceptor fakeInterceptor = new FakeHelperInterceptor(runScopedFakeConfig, recorder);
    Context<?> hierarchyCtx = ctx.withMetadata(
            cbs.nova.dsl.config.Constants.HIERARCHY_GRAPH_ACCUMULATOR_KEY,
            new HierarchyAccumulator());
    return DslExecutionPipeline.<HierarchyReport>builder()
            .stage(new HierarchyReportStage(diagramRenderer))
            .stage(new MetricsStage(meterRegistry))
            .stage(new ExecutionTreeStage(previewProperties.callTree().maxDepth()))
            .stage(new DryRunLogStage(dryRunLoggingContext, bufferRegistry, maxEventsPerRun))
            .stage(new ExecutionTraceStage())
            .stage(new FakingStage(fakesProperties, runScopedFakeConfig))
            .stage(new ExternalCallRecordingStage(recorder))
            .stage(new DispatchStage(fakeInterceptor,
                    Duration.ofMillis(previewProperties.execution().timeoutMs()), executor,
                    meterRegistry, dryRunLoggingContext))
            .build()
            .execute(name, hierarchyCtx);
  }
}
