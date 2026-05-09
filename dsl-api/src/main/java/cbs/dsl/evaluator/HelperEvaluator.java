package cbs.dsl.evaluator;

import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.context.HelperContext;
import cbs.dsl.builder.HelperDslObject;
import org.jspecify.annotations.NonNull;

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
  @NonNull
  public HelperOutput evaluatePreview(@NonNull HelperDslObject dsl, @NonNull HelperInput input) {
    var ctx = HelperContext.<HelperInput>builder()
        .payload(input)
        .build();
    var result = dsl.previewBlock().apply(ctx);
    return result.payload();
  }

  /**
   * Evaluates the execute block of the given helper DSL object.
   *
   * @param dsl the helper DSL object
   * @param input the helper input
   * @return the helper output
   */
  @NonNull
  public HelperOutput evaluateExecute(@NonNull HelperDslObject dsl, @NonNull HelperInput input) {
    var ctx = HelperContext.<HelperInput>builder()
        .payload(input)
        .build();
    var result = dsl.executeBlock().apply(ctx);
    return result.payload();
  }
}
