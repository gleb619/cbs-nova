package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Result;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.List;

@RequiredArgsConstructor
public final class ExternalCallRecordingStage implements DslPipeStage {

  private final ExternalCallRecorder recorder;

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    recorder.startRun(context.getRunId());
    try {
      return next.proceed(context);
    } finally {
      List<ExternalCall> calls = recorder.finishRun(context.getRunId());
      context.setAttribute("externalCalls", calls);
    }
  }
}
