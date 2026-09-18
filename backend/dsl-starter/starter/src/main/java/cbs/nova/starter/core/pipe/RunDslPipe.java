package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.starter.config.properties.CbsNovaFakesProperties;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.core.stage.DispatchStage;
import cbs.nova.starter.core.stage.DslExecutionEventStage;
import cbs.nova.starter.core.stage.ExecutionTraceStage;
import cbs.nova.starter.core.stage.ExternalCallRecordingStage;
import cbs.nova.starter.core.stage.FakingStage;
import cbs.nova.starter.security.ManifestObjectGuard;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public final class RunDslPipe implements DslExecutionPipe<Object> {

  private final ExternalCallRecorder recorder;
  private final CbsNovaFakesProperties fakesProperties;
  private final RunScopedFakeConfig runScopedFakeConfig;
  private final DslExecutionEventBus eventBus;
  private final @Nullable ManifestObjectGuard objectGuard;

  /**
   * Legacy constructor used by tests that do not exercise object-level enforcement.
   */
  public RunDslPipe(
          ExternalCallRecorder recorder,
          CbsNovaFakesProperties fakesProperties,
          RunScopedFakeConfig runScopedFakeConfig,
          DslExecutionEventBus eventBus) {
    this(recorder, fakesProperties, runScopedFakeConfig, eventBus, null);
  }

  @Override
  public @NonNull Result<Object> execute(@NonNull String name,
          @NonNull Context<?> ctx) {
    HelperInterceptor fakeInterceptor = new FakeHelperInterceptor(runScopedFakeConfig, recorder);
    HelperInterceptor dispatchInterceptor = objectGuard != null
            ? new ManifestObjectGuardHelperInterceptor(objectGuard, fakeInterceptor)
            : fakeInterceptor;
    return DslExecutionPipeline.builder()
            .stage(new DslExecutionEventStage(eventBus))
            .stage(new ExecutionTraceStage())
            .stage(new FakingStage(fakesProperties, runScopedFakeConfig))
            .stage(new ExternalCallRecordingStage(recorder))
            .stage(DispatchStage.inline(dispatchInterceptor))
            .build()
            .execute(name, ctx);
  }
}
