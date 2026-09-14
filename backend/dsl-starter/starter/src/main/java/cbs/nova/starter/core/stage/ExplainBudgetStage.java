package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.ExplainReports;
import cbs.nova.dsl.model.ExplainTraceReport;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import org.jspecify.annotations.NonNull;

//TODO: class needs to be reworked due to changes in ExplainReport
@Deprecated(forRemoval = true)
public final class ExplainBudgetStage implements DslPipeStage {

  private final int budgetChars;

  public ExplainBudgetStage(int budgetChars) {
    this.budgetChars = Math.max(0, budgetChars);
  }

  @Override
  @SuppressWarnings("unchecked")
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    Result<?> result = next.proceed(context);
    if (!result.isSuccess()) {
      return result;
    }
    ExplainTraceReport report = (ExplainTraceReport) result.value();
    if (report == null) {
      return result;
    }
    var bounded = ExplainReports.truncateTo(
            new ExplainReport(
                    report.name(),
                    report.description(),
                    report.mermaidDiagram() != null ? report.mermaidDiagram() : ""),
            budgetChars);
    var truncated = new ExplainTraceReport(
            report.name(),
            bounded.description(),
            report.executionTrace(),
            report.externalCalls(),
            report.callCounts(),
            report.executableDescriptor(),
            report.dslDescriptor(),
            report.astTree(),
            report.dryRunLogs(),
            report.metrics(),
            report.errors(),
            bounded.mermaid());
    return Result.success(truncated);
  }
}
