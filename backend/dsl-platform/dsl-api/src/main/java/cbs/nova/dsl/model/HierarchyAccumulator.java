package cbs.nova.dsl.model;

import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.ExecutableDescriptor;

import cbs.nova.dsl.PreviewMetricsSnapshot;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Typed in-progress carrier for one hierarchy run's graph contributions. Pipe stages append their
 * results via the fluent accessors; the report stage finishes the run with
 * {@link #build(String, String, ExecutableDescriptor, DslDescriptor)}. Stored in
 * {@code Context.metadata()} under {@code Constants.HIERARCHY_GRAPH_ACCUMULATOR_KEY}.
 */
public final class HierarchyAccumulator {

  private @Nullable CallNode astTree;
  private @NonNull List<String> executionTrace = List.of();
  private @NonNull List<Map<String, Object>> externalCalls = List.of();
  private @NonNull Map<String, Integer> callCounts = Map.of();
  private @NonNull List<Map<String, Object>> dryRunLogs = List.of();
  private @Nullable PreviewMetricsSnapshot metrics;
  private @NonNull List<ErrorResponse> errors = List.of();
  private boolean hasCompensation;

  public @Nullable CallNode astTree() {
    return astTree;
  }

  public @NonNull HierarchyAccumulator astTree(@Nullable CallNode astTree) {
    this.astTree = astTree;
    return this;
  }

  public @NonNull List<String> executionTrace() {
    return executionTrace;
  }

  public @NonNull HierarchyAccumulator executionTrace(@NonNull List<String> executionTrace) {
    this.executionTrace = List.copyOf(executionTrace);
    return this;
  }

  public @NonNull List<Map<String, Object>> externalCalls() {
    return externalCalls;
  }

  public @NonNull HierarchyAccumulator externalCalls(
          @NonNull List<Map<String, Object>> externalCalls) {
    this.externalCalls = List.copyOf(externalCalls);
    return this;
  }

  public @NonNull Map<String, Integer> callCounts() {
    return callCounts;
  }

  public @NonNull HierarchyAccumulator callCounts(@NonNull Map<String, Integer> callCounts) {
    this.callCounts = Map.copyOf(callCounts);
    return this;
  }

  public @NonNull List<Map<String, Object>> dryRunLogs() {
    return dryRunLogs;
  }

  public @NonNull HierarchyAccumulator dryRunLogs(
          @NonNull List<Map<String, Object>> dryRunLogs) {
    this.dryRunLogs = List.copyOf(dryRunLogs);
    return this;
  }

  public @Nullable PreviewMetricsSnapshot metrics() {
    return metrics;
  }

  public @NonNull HierarchyAccumulator metrics(@Nullable PreviewMetricsSnapshot metrics) {
    this.metrics = metrics;
    return this;
  }

  public @NonNull List<ErrorResponse> errors() {
    return errors;
  }

  public @NonNull HierarchyAccumulator errors(@NonNull List<ErrorResponse> errors) {
    this.errors = List.copyOf(errors);
    return this;
  }

  public boolean hasCompensation() {
    return hasCompensation;
  }

  public @NonNull HierarchyAccumulator hasCompensation(boolean hasCompensation) {
    this.hasCompensation = hasCompensation;
    return this;
  }

  public @NonNull HierarchyReport build(@NonNull String name, @NonNull String description,
          @Nullable ExecutableDescriptor executableDescriptor,
          @Nullable DslDescriptor dslDescriptor) {
    return new HierarchyReport(
            name,
            description,
            executionTrace,
            externalCalls,
            callCounts,
            hasCompensation,
            executableDescriptor,
            dslDescriptor,
            astTree,
            dryRunLogs,
            metrics,
            errors,
            List.of(),
            null);
  }
}
