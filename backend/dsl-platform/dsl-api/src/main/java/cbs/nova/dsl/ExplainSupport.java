package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

/**
 * Contract for producing a human- or AI-oriented explanation of an executable entity.
 * <p>
 * {@code budgetChars} bounds the generated textual payload (description plus any diagram):
 * implementations must truncate rather than exceed it.
 * </p>
 */
@FunctionalInterface
public interface ExplainSupport<IN, OUT> {

  int DEFAULT_BUDGET_CHARS = 4_000;

  @NonNull
  OUT explain(@NonNull Context<IN> ctx, int budgetChars);

  default @NonNull OUT explain(@NonNull Context<IN> ctx) {
    return explain(ctx, DEFAULT_BUDGET_CHARS);
  }

  @Deprecated(forRemoval = true)
  //TODO: it cant be a static method, it must a part of PipeStage instead with some app.yml config
  static @NonNull String truncateToBudget(@NonNull String text, int budgetChars) {
    return text.length() <= budgetChars ? text : text.substring(0, Math.max(budgetChars, 0));
  }
}
