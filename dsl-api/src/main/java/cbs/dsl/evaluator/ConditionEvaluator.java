package cbs.dsl.evaluator;

import cbs.dsl.api.ConditionTypes.ConditionInput;
import cbs.dsl.builder.ConditionDslObject;

/**
 * Evaluates a {@link ConditionDslObject} DSL descriptor at runtime.
 *
 * <p>Interprets the check block directly without generated wrappers.
 */
public class ConditionEvaluator {

  /**
   * Evaluates the condition predicate of the given condition DSL object.
   *
   * @param dsl the condition DSL object
   * @param ctx the transaction context
   * @return the boolean result
   */
  public static boolean evaluate(ConditionDslObject dsl, ConditionInput ctx) {
    if (dsl != null && dsl.evaluateBlock() != null) {
      return dsl.evaluateBlock().apply(ctx).result();
    }
    return false;
  }
}
