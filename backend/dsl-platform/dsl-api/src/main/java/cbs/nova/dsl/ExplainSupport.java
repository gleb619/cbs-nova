package cbs.nova.dsl;

import static cbs.nova.dsl.config.Constants.CURRENT_OBJECT_NAME;

import cbs.nova.dsl.model.ExplainReport;
import org.jspecify.annotations.NonNull;

/**
 * Contract for producing a human- or AI-oriented explanation of an executable entity.
 * <p>
 * The textual budget (description plus any diagram) is carried by the context metadata under
 * {@value cbs.nova.dsl.config.Constants#EXPLAIN_BUDGET_CHARS_KEY}; implementations must truncate
 * rather than exceed it, falling back to {@code 4000} (the default of
 * {@code CbsNovaExplainProperties.budgetChars} in the starter module) when the metadata is absent
 * or invalid.
 * </p>
 */
@FunctionalInterface
public interface ExplainSupport<IN, OUT> {

  @NonNull
  OUT explain(@NonNull Context<IN> ctx);

  default ExplainReport createReport(Context<IN> ctx) {
    // TODO: made a refactoring in
    // `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/DevDslRuntime.java`
    // pipes, on execution, we have a new context object with a current name of object(e.g.
    // process/helper/transaction, etc, e.g.)
    String currentObjectName = ctx.metadata(CURRENT_OBJECT_NAME);
    return ExplainReport.empty(currentObjectName);
  }

}
