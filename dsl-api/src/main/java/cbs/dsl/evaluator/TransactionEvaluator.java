package cbs.dsl.evaluator;

import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.api.context.TransactionContext;
import cbs.dsl.builder.TransactionDslObject;

/**
 * Evaluates a {@link TransactionDslObject} DSL descriptor at runtime.
 *
 * <p>Interprets the preview, execute, and rollback blocks directly without generated Temporal
 * activities.
 */
public class TransactionEvaluator {

  /**
   * Evaluates the preview block of the given transaction DSL object.
   *
   * @param dsl the transaction DSL object
   * @param ctx the transaction context
   * @return the transaction output
   */
  public static TransactionOutput evaluatePreview(
      TransactionDslObject dsl, TransactionContext ctx) {
//    if (dsl != null && dsl.getPreviewBlock() != null) {
//      return dsl.getPreviewBlock().apply(ctx);
//    }
    return TransactionOutput.empty();
  }

  /**
   * Evaluates the execute block of the given transaction DSL object.
   *
   * @param dsl the transaction DSL object
   * @param ctx the transaction context
   * @return the transaction output
   */
  public static TransactionOutput evaluateExecute(
      TransactionDslObject dsl, TransactionContext ctx) {
//    if (dsl != null && dsl.getExecuteBlock() != null) {
//      return dsl.getExecuteBlock().apply(ctx);
//    }
    return TransactionOutput.empty();
  }

  /**
   * Evaluates the rollback block of the given transaction DSL object.
   *
   * @param dsl the transaction DSL object
   * @param ctx the transaction context
   * @return the transaction output
   */
  public static TransactionOutput evaluateRollback(
      TransactionDslObject dsl, TransactionContext ctx) {
//    if (dsl != null && dsl.getRollbackBlock() != null) {
//      return dsl.getRollbackBlock().apply(ctx);
//    }
    return TransactionOutput.empty();
  }
}
