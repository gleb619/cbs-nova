package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Result;
import cbs.nova.starter.converter.ExternalCallConverter;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.pipe.HierarchyAccumulators;
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
    recorder.startRun(context.runId());
    try {
      return next.proceed(context);
    } finally {
      List<ExternalCall> calls = recorder.finishRun(context.runId());
      var accumulator = HierarchyAccumulators.resolve(context);
      if (accumulator.isPresent()) {
        accumulator.get()
                .externalCalls(ExternalCallConverter.toCallJson(calls))
                .callCounts(ExternalCallConverter.toCallCounts(calls));
      } else {
        context.setAttribute(StarterConstants.EXTERNAL_CALLS_ATTRIBUTE, calls);
      }
    }
  }
}
