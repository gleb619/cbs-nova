package cbs.dsl.evaluator;

import cbs.dsl.api.DefinitionRegistry;
import cbs.dsl.api.HelperDefinition;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.context.HelperContext;
import cbs.dsl.api.context.HelperEvaluator;
import cbs.dsl.builder.HelperDslObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Evaluates a {@link HelperDslObject} DSL descriptor at runtime.
 *
 * <p>Interprets the preview and execute blocks directly. Holds a reference to the
 * {@link DefinitionRegistry} so that nested helper resolution can be performed.
 */
@RequiredArgsConstructor
public class RegistryHelperEvaluator implements HelperEvaluator {

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
  public @NonNull HelperContext evaluatePreview(
      @NonNull HelperDslObject dsl, @NonNull HelperInput input) {
    var ctx = HelperContext.builder().build();
    var result = dsl.previewBlock().apply(ctx);
    if (result instanceof HelperContext hctx) {
      return hctx.copy();
    } else {
      var values = new HashMap<>(ctx.params());
      values.put(dsl.code(), values);
      return ctx.toBuilder().params(values).build();
    }
  }

  /**
   * Evaluates the execute block of the given helper DSL object.
   *
   * @param dsl the helper DSL object
   * @param input the helper input
   * @return the helper output
   */
  public @NonNull HelperContext evaluateExecute(
      @NonNull HelperDslObject dsl, @NonNull HelperInput input) {
    var ctx = HelperContext.builder().build();
    var result = dsl.executeBlock().apply(ctx);
    if (result instanceof HelperContext hctx) {
      return hctx.copy();
    } else {
      var values = new HashMap<>(ctx.params());
      values.put(dsl.code(), values);
      return ctx.toBuilder().params(values).build();
    }
  }

  @Override
  public <U> U evaluate(String code, Map<String, Object> params) {
    // TODO: handle preview/execute call here
    HelperOutput output = resolveHelper(code).execute(HelperInput.from(params));
    // TODO: we need a unified result pojo here

    if (output.params().size() == 1) {
      return (U) output.params().entrySet().iterator().next().getValue();
    }

    // TODO: fix problem with types
    return (U) output.params();
  }
}
