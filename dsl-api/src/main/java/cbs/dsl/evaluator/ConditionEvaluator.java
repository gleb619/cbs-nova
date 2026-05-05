package cbs.dsl.evaluator;

import cbs.dsl.api.context.TransactionContext;
import cbs.dsl.builder.ConditionDslObject;

/**
 * Evaluates a {@link ConditionDslObject} DSL descriptor at runtime.
 *
 * <p>Interprets the evaluate block directly without generated wrappers.
 */
public class ConditionEvaluator {

  /**
   * Evaluates the condition predicate of the given condition DSL object.
   *
   * @param dsl the condition DSL object
   * @param ctx the transaction context
   * @return the boolean result
   */
  public static boolean evaluate(ConditionDslObject dsl, TransactionContext ctx) {
    if (dsl != null && dsl.getEvaluateBlock() != null) {
      return dsl.getEvaluateBlock().apply(ctx).result();
    }
    return false;
  }
}
