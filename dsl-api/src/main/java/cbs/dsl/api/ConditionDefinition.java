package cbs.dsl.api;

import cbs.dsl.api.ParametersTypes.ParameterError;
import cbs.dsl.api.ParametersTypes.ParametersInput;
import cbs.dsl.api.context.TransactionContext;

import cbs.dsl.builder.ConditionDslObject;
import java.util.Collections;
import java.util.List;

/**
 * Defines a condition — a reusable boolean predicate that can be referenced from workflow
 * transitions or transaction logic.
 *
 * <p>Implementations are typically created via the Kotlin DSL {@code condition { }} block or
 * annotated with {@link DslComponent} for compile-time registration.
 */
public interface ConditionDefinition extends DslDefinition {

  /**
   * Evaluates this condition with the given typed input.
   *
   * <p>The default implementation builds a {@link TransactionContext} from the input, runs the
   * {@link #getContextBlock()}, invokes {@link #getPredicate()}, and wraps the result in a
   * {@link ConditionTypes.ConditionOutput}.
   *
   * @param input the condition input
   * @return the condition output
   */
  ConditionTypes.ConditionOutput evaluate(ConditionTypes.ConditionInput input);

  /**
   * Returns the DSL object representing this definition.
   *
   * @return the DSL object, or {@code null} if not available
   */
  default ConditionDslObject dsl() {
    throw new NullPointerException("Dsl object not added");
  }
}
