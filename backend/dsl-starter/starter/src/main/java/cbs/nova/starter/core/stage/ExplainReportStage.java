package cbs.nova.starter.core.stage;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainGraphAccumulator;
import cbs.nova.dsl.model.ExplainGraphReport;
import cbs.nova.starter.core.PreviewErrorHandler;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.pipe.ExplainGraphAccumulators;
import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;

public final class ExplainReportStage implements DslPipeStage {

  private final ExplainDiagramRenderer diagramRenderer;

  public ExplainReportStage(ExplainDiagramRenderer diagramRenderer) {
    this.diagramRenderer = diagramRenderer;
  }

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    next.proceed(context);
    Result<?> dslResult = (Result<?>) context.getAttribute(StarterConstants.DSL_RESULT_ATTRIBUTE);
    ExplainGraphAccumulator accumulator = ExplainGraphAccumulators.resolve(context)
            .orElseThrow(() -> new IllegalStateException(
                    "ExplainGraphAccumulator is not threaded into the context metadata"));

    GlobalManager gm = GlobalManager.globalManager();
    DslDescriptor dslDesc = gm.describeProcess(context.name())
            .or(() -> gm.describeTransaction(context.name()))
            .or(() -> gm.describeFunction(context.name()))
            .orElse(null);
    String description = describeEntity(dslDesc, gm, context.name());

    List<ErrorResponse> errors = new ArrayList<>();
    if (dslResult != null && !dslResult.isSuccess()) {
      errors.add(PreviewErrorHandler.from(dslResult.cause(), context.name()));
    }

    accumulator
            .hasCompensation(resolveCompensation(gm, context.name(), dslDesc))
            .errors(errors);

    ExplainGraphReport baseReport = accumulator.build(
            context.name(),
            description,
            gm.describeHelper(context.name()).orElse(null),
            dslDesc);

    String mermaidDiagram = diagramRenderer.mermaidDiagram(baseReport);

    ExplainGraphReport report = new ExplainGraphReport(
            baseReport.name(),
            baseReport.description(),
            baseReport.executionTrace(),
            baseReport.externalCalls(),
            baseReport.callCounts(),
            baseReport.hasCompensation(),
            baseReport.executableDescriptor(),
            baseReport.dslDescriptor(),
            baseReport.astTree(),
            baseReport.dryRunLogs(),
            baseReport.metrics(),
            baseReport.errors(),
            baseReport.children(),
            mermaidDiagram);

    return Result.success(report);
  }

  // TODO: now objects alwasys have a compensations. Fallback is NoOp impl, so compensation is
  // nonnull from now
  @Deprecated(forRemoval = true)
  private boolean resolveCompensation(@NonNull GlobalManager gm, @NonNull String name,
          DslDescriptor dslDesc) {
    if (dslDesc == null) {
      return false;
    }
    return switch (dslDesc.type()) {
      case PROCESS -> gm.findProcess(name)
              .map(process -> process.compensationLogic() != null)
              .orElse(false);
      case TRANSACTION -> gm.findTransaction(name)
              .map(tx -> tx.compensationLogic() != null)
              .orElse(false);
      default -> false;
    };
  }

  private @NonNull String describeEntity(
          DslDescriptor dslDesc, @NonNull GlobalManager gm, @NonNull String name) {
    if (dslDesc != null) {
      return capitalize(dslDesc.type().name()) + ": " + dslDesc.name();
    }
    return gm.describeHelper(name)
            .map(helper -> "Helper: " + name)
            .orElse("Entity: " + name);
  }

  private @NonNull String capitalize(@NonNull String value) {
    if (value.isEmpty()) {
      return value;
    }
    return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
  }
}
