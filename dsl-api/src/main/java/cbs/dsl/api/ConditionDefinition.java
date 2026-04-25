package cbs.dsl.api;

import cbs.dsl.api.context.TransactionContext;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Defines a condition — a reusable boolean predicate that can be referenced from workflow
 * transitions or transaction logic.
 *
 * <p>Implementations are typically created via the Kotlin DSL {@code condition { }} block or
 * annotated with {@link DslComponent} for compile-time registration.
 */
public interface ConditionDefinition {

  /** Canonical code used to look up this condition in the registry. */
  String getCode();

  /**
   * List of parameter definitions declared in the {@code parameters { }} block. Used for validation
   * and documentation purposes.
   */
  default List<ParameterDefinition> getParameters() {
    return Collections.emptyList();
  }

  /**
   * Optional context enrichment block that runs before the predicate is evaluated. Allows
   * conditions to enrich the context with additional data before evaluation.
   */
  default Consumer<TransactionContext> getContextBlock() {
    return ctx -> {};
  }

  /** The predicate that determines whether this condition holds. */
  Predicate<TransactionContext> getPredicate();

  /**
   * Evaluates this condition with the given typed input.
   *
   * <p>The default implementation builds a {@link TransactionContext} from the input, runs the
   * {@link #getContextBlock()}, invokes {@link #getPredicate()}, and wraps the result in a {@link
   * ConditionTypes.ConditionOutput}.
   */
  default ConditionTypes.ConditionOutput evaluate(ConditionTypes.ConditionInput input) {
    TransactionContext ctx =
        TransactionContext.transactionBuilder()
            .eventCode(input.eventCode() != null ? input.eventCode() : "")
            .workflowExecutionId(input.eventNumber() != null ? input.eventNumber() : 0L)
            .performedBy("")
            .dslVersion("")
            .eventParameters(input.nonNullParams())
            .isResumed(false)
            .build();
    getContextBlock().accept(ctx);
    boolean result = getPredicate().test(ctx);
    return new ConditionTypes.ConditionOutput(result);
  }
}
