package cbs.nova.starter;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.starter.core.pipe.ExplainDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunDslPipe;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class DevDslRuntime implements DslRuntime {

  private final PreviewDslPipe previewPipe;
  private final RunDslPipe runPipe;
  private final ExplainDslPipe explainPipe;

  @Override
  public @NonNull Result<PreviewReport> preview(@NonNull String name, @NonNull Context<?> ctx) {
    return previewPipe.execute(name, ctx);
  }

  @Override
  public @NonNull Result<?> run(@NonNull String name, @NonNull Context<?> ctx) {
    return runPipe.execute(name, ctx);
  }

  @Override
  public @NonNull ExplainReport explain(@NonNull String name, @NonNull Context<?> ctx) {
    Result<ExplainReport> result = explainPipe.execute(name, ctx);
    //TODO: add check for null, add default value case
    return result.value();
  }
}
