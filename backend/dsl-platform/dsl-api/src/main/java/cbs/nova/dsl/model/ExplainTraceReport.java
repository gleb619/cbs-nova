package cbs.nova.dsl.model;

import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record ExplainTraceReport(
        @NonNull String name,
        @NonNull String description,
        @NonNull List<String> executionTrace,
        @NonNull List<Map<String, Object>> externalCalls,
        @NonNull Map<String, Integer> callCounts,
        @Nullable ExecutableDescriptor executableDescriptor,
        @Nullable DslDescriptor dslDescriptor,
        @Nullable CallNode astTree,
        @NonNull List<Map<String, Object>> dryRunLogs,
        @Nullable PreviewMetricsSnapshot metrics,
        @Nullable List<PreviewErrorDetail> errors,
        @Nullable String mermaidDiagram) {

  public ExplainTraceReport {
    dryRunLogs = dryRunLogs == null ? List.of() : List.copyOf(dryRunLogs);
    errors = errors == null ? List.of() : List.copyOf(errors);
  }
}
