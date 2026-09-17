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
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class RunDslPipe implements DslExecutionPipe<Object> {

  private final ExternalCallRecorder recorder;
  private final CbsNovaFakesProperties fakesProperties;
  private final RunScopedFakeConfig runScopedFakeConfig;
  private final DslExecutionEventBus eventBus;

  @Override
  public @NonNull Result<Object> execute(@NonNull String name,
          @NonNull Context<?> ctx) {
    HelperInterceptor fakeInterceptor = new FakeHelperInterceptor(runScopedFakeConfig, recorder);
    return DslExecutionPipeline.builder()
            .stage(new DslExecutionEventStage(eventBus))
            .stage(new ExecutionTraceStage())
            .stage(new FakingStage(fakesProperties, runScopedFakeConfig))
            .stage(new ExternalCallRecordingStage(recorder))
            .stage(DispatchStage.inline(fakeInterceptor))
            .build()
            .execute(name, ctx);
  }
}
