package cbs.nova.starter;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.HierarchyReport;
import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.starter.core.PreviewErrorHandler;
import cbs.nova.starter.core.pipe.ExplainDslPipe;
import cbs.nova.starter.core.pipe.HierarchyDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunDslPipe;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class DevDslRuntime implements DslRuntime {

  private final PreviewDslPipe previewPipe;
  private final RunDslPipe runPipe;
  private final HierarchyDslPipe hierarchyPipe;
  private final ExplainDslPipe explainPipe;

  @Override
  public @NonNull Result<PreviewReport> preview(@NonNull String name, @NonNull Context<?> ctx) {
    return previewPipe.execute(name, ctx);
  }

  @Override
  public @NonNull Result<HierarchyReport> hierarchy(@NonNull String name,
          @NonNull Context<?> ctx) {
    return hierarchyPipe.execute(name, ctx);
  }

  @Override
  public @NonNull Result<?> run(@NonNull String name, @NonNull Context<?> ctx) {
    return runPipe.execute(name, ctx);
  }

  @Override
  public @NonNull ExplainReport explain(@NonNull String name, @NonNull Context<?> ctx) {
    Result<ExplainReport> result = explainPipe.execute(name, ctx);
    ExplainReport report = result.value();
    if (report != null) {
      return report;
    }
    ErrorResponse error = PreviewErrorHandler.from(result.cause(), name);
    return new ExplainReport(
            name,
            "Entity: " + name + " — explain failed: " + error.message(),
            "",
            List.of());
  }
}
