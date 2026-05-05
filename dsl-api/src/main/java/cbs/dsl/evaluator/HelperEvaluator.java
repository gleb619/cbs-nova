package cbs.dsl.evaluator;

import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.builder.HelperDslObject;

/**
 * Evaluates a {@link HelperDslObject} DSL descriptor at runtime.
 *
 * <p>Interprets the preview and execute blocks directly.
 */
public class HelperEvaluator {

  /**
   * Evaluates the preview block of the given helper DSL object.
   *
   * @param dsl the helper DSL object
   * @param input the helper input
   * @return the helper output
   */
  public static HelperOutput evaluatePreview(HelperDslObject dsl, HelperInput input) {
    if (dsl != null && dsl.getPreviewBlock() != null) {
      return dsl.getPreviewBlock().apply(input);
    }
    return evaluateExecute(dsl, input);
  }

  /**
   * Evaluates the execute block of the given helper DSL object.
   *
   * @param dsl the helper DSL object
   * @param input the helper input
   * @return the helper output
   */
  public static HelperOutput evaluateExecute(HelperDslObject dsl, HelperInput input) {
    if (dsl != null && dsl.getExecuteBlock() != null) {
      return dsl.getExecuteBlock().apply(input);
    }
    return new HelperOutput(null);
  }
}
