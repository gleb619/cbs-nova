package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record ExplainReport(
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

  public ExplainReport {
    dryRunLogs = dryRunLogs == null ? List.of() : List.copyOf(dryRunLogs);
    errors = errors == null ? List.of() : List.copyOf(errors);
  }
}
