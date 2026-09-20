package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.starter.config.properties.CbsNovaExplainProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.explain.ExplainBudget;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

/**
 * Explain stage: hands the run to the downstream dispatch in {@code ExecutionMode.EXPLAIN} (the
 * entity's own explain graph already comes back as an {@link ExplainReport}), then applies token
 * caps plus the character budget to that report. Independent of hierarchy mode.
 */
@RequiredArgsConstructor
public final class ExplainReportStage implements DslPipeStage {

  private final CbsNovaExplainProperties explainProperties;

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    Context<?> budgeted = context.dslContext().withMetadata(
            Constants.EXPLAIN_BUDGET_CHARS_KEY, explainProperties.budgetChars());
    next.proceed(context.withDslContext(budgeted));

    Result<?> dispatched = (Result<?>) context.getAttribute(StarterConstants.DSL_RESULT_ATTRIBUTE);
    if (dispatched == null || !dispatched.isSuccess()) {
      return Result.failure(dispatched != null && dispatched.cause() != null
              ? dispatched.cause()
              : new IllegalStateException("explain produced no report for " + context.name()));
    }
    if (!(dispatched.value() instanceof ExplainReport report)) {
      return Result.failure(new IllegalStateException(
              "explain of " + context.name() + " did not return an ExplainReport"));
    }
    return Result.success(ExplainBudget.apply(
            withDescription(report),
            explainProperties.budgetChars(),
            explainProperties.nameMaxTokens(),
            explainProperties.descriptionMaxTokens(),
            explainProperties.mermaidMaxTokens()));
  }

  private @NonNull ExplainReport withDescription(@NonNull ExplainReport report) {
    if (!report.description().isBlank()) {
      return report;
    }
    GlobalManager gm = GlobalManager.globalManager();
    return gm.describeProcess(report.name())
            .or(() -> gm.describeTransaction(report.name()))
            .or(() -> gm.describeFunction(report.name()))
            .map(descriptor -> report.withInfo(report.name(),
                    capitalize(descriptor.type().name()) + ": " + report.name()))
            .orElse(report);
  }

  private static @NonNull String capitalize(@NonNull String value) {
    return value.isEmpty()
            ? value
            : Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
  }
}
