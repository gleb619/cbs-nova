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
 * Typed in-progress carrier for one explain run's graph contributions. Pipe stages append their
 * results via the fluent accessors; the report stage finishes the run with
 * {@link #build(String, String, ExecutableDescriptor, DslDescriptor)}. Stored in
 * {@code Context.metadata()} under {@code Constants.EXPLAIN_GRAPH_ACCUMULATOR_KEY}.
 */
public final class ExplainGraphAccumulator {

  private @Nullable CallNode astTree;
  private @NonNull List<String> executionTrace = List.of();
  private @NonNull List<Map<String, Object>> externalCalls = List.of();
  private @NonNull Map<String, Integer> callCounts = Map.of();
  private @NonNull List<Map<String, Object>> dryRunLogs = List.of();
  private @Nullable PreviewMetricsSnapshot metrics;
  private @NonNull List<PreviewErrorDetail> errors = List.of();
  private boolean hasCompensation;

  public @Nullable CallNode astTree() {
    return astTree;
  }

  public @NonNull ExplainGraphAccumulator astTree(@Nullable CallNode astTree) {
    this.astTree = astTree;
    return this;
  }

  public @NonNull List<String> executionTrace() {
    return executionTrace;
  }

  public @NonNull ExplainGraphAccumulator executionTrace(@NonNull List<String> executionTrace) {
    this.executionTrace = List.copyOf(executionTrace);
    return this;
  }

  public @NonNull List<Map<String, Object>> externalCalls() {
    return externalCalls;
  }

  public @NonNull ExplainGraphAccumulator externalCalls(
          @NonNull List<Map<String, Object>> externalCalls) {
    this.externalCalls = List.copyOf(externalCalls);
    return this;
  }

  public @NonNull Map<String, Integer> callCounts() {
    return callCounts;
  }

  public @NonNull ExplainGraphAccumulator callCounts(@NonNull Map<String, Integer> callCounts) {
    this.callCounts = Map.copyOf(callCounts);
    return this;
  }

  public @NonNull List<Map<String, Object>> dryRunLogs() {
    return dryRunLogs;
  }

  public @NonNull ExplainGraphAccumulator dryRunLogs(
          @NonNull List<Map<String, Object>> dryRunLogs) {
    this.dryRunLogs = List.copyOf(dryRunLogs);
    return this;
  }

  public @Nullable PreviewMetricsSnapshot metrics() {
    return metrics;
  }

  public @NonNull ExplainGraphAccumulator metrics(@Nullable PreviewMetricsSnapshot metrics) {
    this.metrics = metrics;
    return this;
  }

  public @NonNull List<PreviewErrorDetail> errors() {
    return errors;
  }

  public @NonNull ExplainGraphAccumulator errors(@NonNull List<PreviewErrorDetail> errors) {
    this.errors = List.copyOf(errors);
    return this;
  }

  public boolean hasCompensation() {
    return hasCompensation;
  }

  public @NonNull ExplainGraphAccumulator hasCompensation(boolean hasCompensation) {
    this.hasCompensation = hasCompensation;
    return this;
  }

  public @NonNull ExplainGraphReport build(@NonNull String name, @NonNull String description,
          @Nullable ExecutableDescriptor executableDescriptor,
          @Nullable DslDescriptor dslDescriptor) {
    return new ExplainGraphReport(
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
