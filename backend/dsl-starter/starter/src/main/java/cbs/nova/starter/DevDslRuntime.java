package cbs.nova.starter;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainGraphReport;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.starter.core.PreviewErrorHandler;
import cbs.nova.starter.core.pipe.ExplainDslPipe;
import cbs.nova.starter.core.pipe.PreviewDslPipe;
import cbs.nova.starter.core.pipe.RunDslPipe;
import java.util.List;
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
    Result<ExplainGraphReport> result = explainPipe.execute(name, ctx);
    ExplainGraphReport traceReport = result.value();
    if (traceReport != null) {
      return toExplainReport(traceReport);
    }
    ErrorResponse error = PreviewErrorHandler.from(result.cause(), name);
    return new ExplainReport(
            name,
            "Entity: " + name + " — explain failed: " + error.message(),
            "",
            List.of());
  }

  private static @NonNull ExplainReport toExplainReport(@NonNull ExplainGraphReport traceReport) {
    var description = traceReport.description() + traceSummary(traceReport);
    var mermaid = traceReport.mermaidDiagram() != null ? traceReport.mermaidDiagram() : "";
    return new ExplainReport(traceReport.name(), description, mermaid, List.of());
  }

  private static @NonNull String traceSummary(@NonNull ExplainGraphReport traceReport) {
    var traceEntries = traceReport.executionTrace().size();
    var errors = traceReport.errors().size();
    if (traceEntries == 0 && errors == 0) {
      return "";
    }
    return "\n\nTrace: " + traceEntries + " entries, " + errors + " errors.";
  }
}
