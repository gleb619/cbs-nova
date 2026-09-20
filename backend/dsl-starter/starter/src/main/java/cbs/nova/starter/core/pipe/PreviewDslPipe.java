package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.starter.config.properties.CbsNovaFakesProperties;
import cbs.nova.starter.config.properties.CbsNovaPreviewProperties;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.core.stage.CurrentObjectNameStage;
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
import cbs.nova.starter.security.ManifestObjectGuard;
import cbs.nova.starter.service.PreviewResultCache;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.ExecutorService;

@RequiredArgsConstructor
public final class PreviewDslPipe implements DslExecutionPipe<PreviewReport> {

  private final ExternalCallRecorder recorder;
  private final DryRunLoggingContext dryRunLoggingContext;
  private final DryRunLogBufferRegistry bufferRegistry;
  private final int maxEventsPerRun;
  private final PreviewResultCache cache;
  private final CbsNovaPreviewProperties previewProperties;
  private final CbsNovaFakesProperties fakesProperties;
  private final RunScopedFakeConfig runScopedFakeConfig;
  private final MeterRegistry meterRegistry;
  private final ExecutorService executor;
  private final @Nullable ManifestObjectGuard objectGuard;

  /**
   * Legacy constructor used by tests that do not exercise object-level enforcement.
   */
  public PreviewDslPipe(
          ExternalCallRecorder recorder,
          DryRunLoggingContext dryRunLoggingContext,
          DryRunLogBufferRegistry bufferRegistry,
          int maxEventsPerRun,
          PreviewResultCache cache,
          CbsNovaPreviewProperties previewProperties,
          CbsNovaFakesProperties fakesProperties,
          RunScopedFakeConfig runScopedFakeConfig,
          MeterRegistry meterRegistry,
          ExecutorService executor) {
    this(recorder, dryRunLoggingContext, bufferRegistry, maxEventsPerRun, cache,
            previewProperties, fakesProperties, runScopedFakeConfig, meterRegistry, executor,
            null);
  }

  @Override
  public @NonNull Result<PreviewReport> execute(@NonNull String name,
          @NonNull Context<?> ctx) {
    HelperInterceptor fakeInterceptor = new FakeHelperInterceptor(runScopedFakeConfig, recorder);
    HelperInterceptor dispatchInterceptor = objectGuard != null
            ? new ManifestObjectGuardHelperInterceptor(objectGuard, fakeInterceptor)
            : fakeInterceptor;
    return DslExecutionPipeline.<PreviewReport>builder()
            .stage(new CurrentObjectNameStage())
            .stage(new PreviewCacheStage(cache))
            .stage(new PreviewReportStage())
            .stage(new MetricsStage(meterRegistry))
            .stage(new ExecutionTreeStage(
                    previewProperties.callTree().maxDepth()))
            .stage(new DryRunLogStage(dryRunLoggingContext, bufferRegistry, maxEventsPerRun))
            .stage(new ExecutionTraceStage())
            .stage(new FakingStage(fakesProperties, runScopedFakeConfig))
            .stage(new ExternalCallRecordingStage(recorder))
            .stage(new DispatchStage(dispatchInterceptor,
                    Duration.ofMillis(previewProperties.execution().timeoutMs()), executor,
                    meterRegistry, dryRunLoggingContext))
            .build()
            .execute(name, ctx);
  }
}
