package cbs.nova.starter.core.stage;

import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.ExplainGraphReport;
import cbs.nova.starter.converter.ExternalCallConverter;
import cbs.nova.starter.core.PreviewErrorHandler;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;

public final class ExplainReportStage implements DslPipeStage {

  private final ExplainDiagramRenderer diagramRenderer;

  public ExplainReportStage(ExplainDiagramRenderer diagramRenderer) {
    this.diagramRenderer = diagramRenderer;
  }

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    Result<?> inner = next.proceed(context);
    Result<?> dslResult = (Result<?>) context.getAttribute("dslResult");

    GlobalManager gm = GlobalManager.globalManager();
    DslDescriptor dslDesc = gm.describeProcess(context.name())
            .or(() -> gm.describeTransaction(context.name()))
            .or(() -> gm.describeFunction(context.name()))
            .orElse(null);
    String description = describeEntity(dslDesc, gm, context.name());
    boolean hasCompensation = resolveCompensation(gm, context.name(), dslDesc);

    List<PreviewErrorDetail> errors = new ArrayList<>();
    if (dslResult != null && !dslResult.isSuccess()) {
      errors.add(PreviewErrorHandler.from(dslResult.cause(), context.name()));
    }

    @SuppressWarnings("unchecked")
    List<ExternalCall> calls = (List<ExternalCall>) context.getAttribute("externalCalls");
    List<Map<String, Object>> externalCalls = calls != null
            ? ExternalCallConverter.toCallJson(calls)
            : List.of();
    Map<String, Integer> callCounts = calls != null
            ? ExternalCallConverter.toCallCounts(calls)
            : Map.of();

    ExplainGraphReport baseReport = new ExplainGraphReport(
            context.name(),
            description,
            attribute(context, "executionTrace", List.class, List.of()),
            externalCalls,
            callCounts,
            hasCompensation,
            gm.describeHelper(context.name()).orElse(null),
            dslDesc,
            context.getAttribute("astTree", CallNode.class),
            attribute(context, "dryRunLogs", List.class, List.of()),
            context.getAttribute("metrics", PreviewMetricsSnapshot.class),
            errors,
            List.of(),
            null);

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

  //TODO: now objects alwasys have a compensations. Fallback is NoOp impl, so compensation is nonnull from now
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

  @SuppressWarnings("unchecked")
  private <T> T attribute(@NonNull DslPipeContext context, @NonNull String key,
          @NonNull Class<T> type, T defaultValue) {
    T value = context.getAttribute(key, type);
    return value != null ? value : defaultValue;
  }
}
