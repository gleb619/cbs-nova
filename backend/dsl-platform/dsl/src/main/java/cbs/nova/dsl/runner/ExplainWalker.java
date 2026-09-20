package cbs.nova.dsl.runner;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.listener.ExecutionListener;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.transaction.TransactionExecution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Composes an entity's explain graph in {@code ExecutionMode.EXPLAIN}: the entity's own report
 * comes from its explain logic, its callees are discovered by walking its preview logic under the
 * same mode, where every nested process/transaction/helper/function call answers with its own
 * {@link ExplainReport}. Direct children reported during the walk are attached to the own report.
 */
final class ExplainWalker implements ExecutionListener {

  private final Map<String, ExplainReport> children = new LinkedHashMap<>();

  static @NonNull Result<ExplainReport> withChildren(
          @NonNull Context<?> ctx,
          @NonNull Result<ExplainReport> own,
          @NonNull Consumer<Context<?>> walk) {
    ExplainReport report = own.value();
    if (!own.isSuccess() || report == null) {
      return own;
    }
    ExplainWalker walker = new ExplainWalker();
    try {
      walk.accept(ctx.withExecutionListener(walker));
    } catch (RuntimeException ignored) {
      // discovery is best effort; the entity's own report is still valid
    }
    if (walker.children.isEmpty()) {
      return own;
    }
    Map<String, ExplainReport> merged = new LinkedHashMap<>();
    report.children().forEach(child -> merged.put(child.name(), child));
    walker.children.forEach(merged::putIfAbsent);
    return Result.success(ExplainReport.builder()
            .name(report.name())
            .description(report.description())
            .markdown(report.markdown())
            .children(new ArrayList<>(merged.values()))
            .build());
  }

  private void collect(@Nullable Object output, boolean success) {
    if (success && output instanceof ExplainReport report) {
      children.putIfAbsent(report.name(), report);
    }
  }

  @Override
  public void onTransactionSuccess(@NonNull TransactionExecution execution) {
  }

  @Override
  public void onTransactionFailure(@NonNull String runId, @NonNull String transactionName,
          @NonNull Throwable cause) {
  }

  @Override
  public void onTransactionEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    collect(output, success);
  }

  @Override
  public void onHelperEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    collect(output, success);
  }

  @Override
  public void onFunctionEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    collect(output, success);
  }
}
