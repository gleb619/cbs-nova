package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record PreviewReport(
        @NonNull String name,
        @NonNull ExecutionMode mode,
        boolean success,
        @Nullable Object output,
        @NonNull List<String> executionTrace,
        @NonNull List<Map<String, Object>> externalCalls,
        @NonNull Map<String, Integer> callCounts,
        @Nullable CallNode astTree,
        @NonNull List<Map<String, Object>> dryRunLogs) {

  public PreviewReport {
    dryRunLogs = dryRunLogs == null ? List.of() : List.copyOf(dryRunLogs);
  }
}
