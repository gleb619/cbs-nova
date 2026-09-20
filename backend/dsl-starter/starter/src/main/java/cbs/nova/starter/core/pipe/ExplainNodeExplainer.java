package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.function.FunctionRichContext;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.process.ProcessRichContext;
import cbs.nova.dsl.transaction.TransactionRichContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Invokes the entity's own explain function for one hierarchy node: processes, transactions and
 * functions through {@code effectiveExplain()}/{@code explainLogic()}, helpers through
 * {@code Executable.explain(ctx)}. Returns {@code null} when the entity is missing or its explain
 * fails, so callers can fall back to synthesized descriptions.
 */
final class ExplainNodeExplainer {

  private final int budgetChars;

  ExplainNodeExplainer(int budgetChars) {
    this.budgetChars = budgetChars;
  }

  @Nullable
  ExplainReport explain(@NonNull String name, @NonNull Context<?> requestCtx) {
    GlobalManager gm = GlobalManager.globalManager();
    Context<?> explainCtx = explainContext(requestCtx);
    ExplainReport report = runExplainer(() -> gm.findProcess(name)
            .map(process -> process.explainLogic().apply(new ProcessRichContext<>(explainCtx)))
            .orElse(null));
    if (report == null) {
      report = runExplainer(() -> gm.findTransaction(name)
              .map(tx -> tx.effectiveExplain().apply(new TransactionRichContext<>(explainCtx)))
              .orElse(null));
    }
    if (report == null) {
      report = runExplainer(() -> gm.findFunction(name)
              .map(fn -> fn.effectiveExplain().apply(new FunctionRichContext<>(explainCtx)))
              .orElse(null));
    }
    if (report == null) {
      report = explainHelper(gm, name, explainCtx);
    }
    return report;
  }

  private @NonNull Context<?> explainContext(@NonNull Context<?> requestCtx) {
    Map<String, Object> metadata = new LinkedHashMap<>(requestCtx.metadata());
    metadata.put(Constants.EXPLAIN_BUDGET_CHARS_KEY, budgetChars);
    return SimpleContext.builder()
            .body(requestCtx.body())
            .metadata(Map.copyOf(metadata))
            .mode(ExecutionMode.EXPLAIN)
            .runId(requestCtx.runId())
            .build();
  }

  private @Nullable ExplainReport runExplainer(
          @NonNull Supplier<@Nullable Result<ExplainReport>> explainer) {
    try {
      Result<ExplainReport> result = explainer.get();
      return result != null && result.isSuccess() ? result.value() : null;
    } catch (RuntimeException ex) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private @Nullable ExplainReport explainHelper(
          @NonNull GlobalManager gm, @NonNull String name, @NonNull Context<?> explainCtx) {
    try {
      return gm.findHelper(name)
              .map(helper -> ((Executable<Object, ?>) helper)
                      .explain((Context<Object>) explainCtx))
              .orElse(null);
    } catch (RuntimeException ex) {
      return null;
    }
  }
}
