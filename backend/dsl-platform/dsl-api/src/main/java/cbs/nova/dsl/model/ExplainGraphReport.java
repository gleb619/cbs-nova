package cbs.nova.dsl.model;

import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One node of the explain call graph: the full execution trace of a single call plus links
 * ({@code children}) to the reports of the entities it calls. Diagrams are derived on demand from
 * the report's own fields — {@code name}, {@code dslDescriptor}, {@code hasCompensation},
 * {@code externalCalls}, {@code callCounts}, {@code children} — with no live registry lookups, via
 * {@link #toMermaid()}, {@link #toPlantUml()} and {@link #toBpmn()}.
 */
public record ExplainGraphReport(
        @NonNull String name,
        @NonNull String description,
        @NonNull List<String> executionTrace,
        @NonNull List<Map<String, Object>> externalCalls,
        @NonNull Map<String, Integer> callCounts,
        boolean hasCompensation,
        @Nullable ExecutableDescriptor executableDescriptor,
        @Nullable DslDescriptor dslDescriptor,
        @Nullable CallNode astTree,
        @NonNull List<Map<String, Object>> dryRunLogs,
        @Nullable PreviewMetricsSnapshot metrics,
        @Nullable List<PreviewErrorDetail> errors,
        @NonNull List<ExplainGraphReport> children,
        @Nullable String mermaidDiagram) {

  public ExplainGraphReport {
    dryRunLogs = dryRunLogs == null ? List.of() : List.copyOf(dryRunLogs);
    errors = errors == null ? List.of() : List.copyOf(errors);
    children = children == null ? List.of() : List.copyOf(children);
  }

  public @NonNull String toMermaid() {
    return ExplainGraphDiagrams.mermaid(this);
  }

  public @NonNull String toPlantUml() {
    return ExplainGraphDiagrams.plantUml(this);
  }

  public @NonNull String toBpmn() {
    return ExplainGraphDiagrams.bpmn(this);
  }
}
