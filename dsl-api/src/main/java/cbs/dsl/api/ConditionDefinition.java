package cbs.dsl.api;

import cbs.dsl.api.ConditionTypes.ConditionInput;
import cbs.dsl.api.ConditionTypes.ConditionOutput;
import cbs.dsl.api.context.TransactionContext;

/**
 * Defines a condition — a reusable boolean predicate that can be referenced from workflow
 * transitions or transaction logic.
 *
 * <p>Implementations are typically created via the Kotlin DSL {@code condition { }} block or
 * annotated with {@link DslComponent} for compile-time registration.
 */
public interface ConditionDefinition extends StandardDslDefinition {

  /**
   * Evaluates this condition with the given typed input.
   *
   * <p>The default implementation builds a {@link TransactionContext} from the input, runs the
   * {@link #getContextBlock()}, invokes {@link #getPredicate()}, and wraps the result in a
   * {@link ConditionOutput}.
   *
   * @param input the condition input
   * @return the condition output
   */
  ConditionOutput check(ConditionInput input);
}
