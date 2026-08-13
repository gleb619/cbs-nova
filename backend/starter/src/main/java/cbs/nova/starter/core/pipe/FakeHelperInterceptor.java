package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.HelperInterceptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.fake.FakeConfig;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

@RequiredArgsConstructor
public final class FakeHelperInterceptor implements HelperInterceptor {

  private final RunScopedFakeConfig runScopedFakeConfig;
  private final ExternalCallRecorder recorder;

  @Override
  public @NonNull Optional<Result<?>> intercept(@NonNull String helperName,
          @NonNull Context<?> ctx) {
    FakeConfig config = runScopedFakeConfig.find(ctx.runId());
    if (config == null) {
      return Optional.empty();
    }
    Object response = config.findResponse("helper", helperName);
    if (response == null) {
      response = config.findResponse("function", helperName);
    }
    if (response == null) {
      return Optional.empty();
    }
    recorder.record("helper", helperName, "execute", response);
    return Optional.of(Result.success(response));
  }
}
