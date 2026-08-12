package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Result;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.core.recorder.MapBasedMockResolver;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class ExternalCallRecordingStage implements DslPipeStage {

  private final ExternalCallRecorder recorder;

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    recorder.startRun(context.getRunId());
    startMocking(context);
    try {
      return next.proceed(context);
    } finally {
      List<ExternalCall> calls = recorder.finishRun(context.getRunId());
      recorder.stopMocking();
      context.setAttribute("externalCalls", calls);
    }
  }

  private void startMocking(@NonNull DslPipeContext context) {
    Object mocks = context.getDslContext().metadata().get("cbs.nova.preview.mocks");
    if (mocks instanceof Map<?, ?> map && !map.isEmpty()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> typed = (Map<String, Object>) map;
      recorder.startMocking(new MapBasedMockResolver(typed));
    }
  }
}
