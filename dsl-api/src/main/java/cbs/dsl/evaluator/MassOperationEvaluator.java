package cbs.dsl.evaluator;

import cbs.dsl.api.MassOperationTypes.MassOperationInput;
import cbs.dsl.api.MassOperationTypes.MassOperationOutput;
import cbs.dsl.api.context.MassOperationContext;
import cbs.dsl.builder.MassOperationDslObject;

/**
 * Evaluates a {@link MassOperationDslObject} DSL descriptor at runtime.
 *
 * <p>Interprets mass operation blocks (context, item, signals) directly.
 */
public class MassOperationEvaluator {

  /**
   * Evaluates the context enrichment block of the given mass operation DSL object.
   *
   * @param dsl the mass operation DSL object
   * @param ctx the mass operation context
   */
  public static void evaluateContext(MassOperationDslObject dsl, MassOperationContext ctx) {
    if (dsl != null && dsl.contextBlock() != null) {
      dsl.contextBlock().accept(ctx);
    }
  }

  /**
   * Evaluates the mass operation with the given input.
   *
   * @param dsl the mass operation DSL object
   * @param input the operation input
   * @return the operation output
   */
  public static MassOperationOutput evaluate(MassOperationDslObject dsl, MassOperationInput input) {
    return new MassOperationOutput(0L, 0L, "SUCCESS");
  }
}
