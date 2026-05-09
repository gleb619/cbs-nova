package cbs.dsl.evaluator;

import cbs.dsl.api.DefinitionRegistry;
import cbs.dsl.api.HelperDefinition;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.context.HelperContext;
import cbs.dsl.builder.HelperDslObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

/**
 * Evaluates a {@link HelperDslObject} DSL descriptor at runtime.
 *
 * <p>Interprets the preview and execute blocks directly. Holds a reference to the
 * {@link DefinitionRegistry} so that nested helper resolution can be performed.
 */
@RequiredArgsConstructor
public class HelperEvaluator {

  private final DefinitionRegistry registry;

  /**
   * Resolves a helper definition by code from the registry.
   *
   * @param code the helper code
   * @return the helper definition
   * @throws IllegalArgumentException if not found
   */
  @NonNull
  public HelperDefinition resolveHelper(@NonNull String code) {
    return registry.resolveHelper(code);
  }

  /**
   * Evaluates the preview block of the given helper DSL object.
   *
   * @param dsl the helper DSL object
   * @param input the helper input
   * @return the helper output
   */
  public @NonNull HelperContext evaluatePreview(@NonNull HelperDslObject dsl, @NonNull HelperInput input) {
    var ctx = HelperContext.builder()
        .build();
    var result = dsl.previewBlock().apply(ctx);
    if (result instanceof HelperContext hctx) {
      return hctx.copy();
    } else {
      //TODO: how to handle custom response
      return null;
    }
  }

  /**
   * Evaluates the execute block of the given helper DSL object.
   *
   * @param dsl the helper DSL object
   * @param input the helper input
   * @return the helper output
   */
  public @NonNull HelperContext evaluateExecute(@NonNull HelperDslObject dsl, @NonNull HelperInput input) {
    var ctx = HelperContext.builder()
        .build();
    var result = dsl.executeBlock().apply(ctx);
    if (result instanceof HelperContext hctx) {
      return hctx.copy();
    } else {
      //TODO: how to handle custom response
      return null;
    }
  }
}
