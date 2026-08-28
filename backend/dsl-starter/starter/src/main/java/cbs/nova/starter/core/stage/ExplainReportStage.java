package cbs.nova.starter.core.stage;

import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import cbs.nova.dsl.Result;
import cbs.nova.starter.core.PreviewErrorHandler;
import cbs.nova.starter.converter.ExternalCallConverter;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    DslDescriptor dslDesc = gm.describeProcess(context.getName())
            .or(() -> gm.describeTransaction(context.getName()))
            .or(() -> gm.describeFunction(context.getName()))
            .orElse(null);
    String description = describeEntity(dslDesc, gm, context.getName());

    List<PreviewErrorDetail> errors = new ArrayList<>();
    if (dslResult != null && !dslResult.isSuccess()) {
      errors.add(PreviewErrorHandler.from(dslResult.cause(), context.getName()));
    }

    @SuppressWarnings("unchecked")
    List<ExternalCall> calls = (List<ExternalCall>) context.getAttribute("externalCalls");
    List<Map<String, Object>> externalCalls = calls != null
            ? ExternalCallConverter.toCallJson(calls)
            : List.of();
    Map<String, Integer> callCounts = calls != null
            ? ExternalCallConverter.toCallCounts(calls)
            : Map.of();

    ExplainReport baseReport = new ExplainReport(
            context.getName(),
            description,
            attribute(context, "executionTrace", List.class, List.of()),
            externalCalls,
            callCounts,
            gm.describeHelper(context.getName()).orElse(null),
            dslDesc,
            context.getAttribute("astTree", CallNode.class),
            attribute(context, "dryRunLogs", List.class, List.of()),
            context.getAttribute("metrics", PreviewMetricsSnapshot.class),
            errors,
            null);

    String mermaidDiagram = diagramRenderer.mermaidDiagram(baseReport);

    ExplainReport report = new ExplainReport(
            baseReport.name(),
            baseReport.description(),
            baseReport.executionTrace(),
            baseReport.externalCalls(),
            baseReport.callCounts(),
            baseReport.executableDescriptor(),
            baseReport.dslDescriptor(),
            baseReport.astTree(),
            baseReport.dryRunLogs(),
            baseReport.metrics(),
            baseReport.errors(),
            mermaidDiagram);

    return Result.success(report);
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
