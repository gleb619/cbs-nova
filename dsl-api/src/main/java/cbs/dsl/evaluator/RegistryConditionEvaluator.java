package cbs.dsl.evaluator;

import cbs.dsl.api.ConditionDefinition;
import cbs.dsl.api.ConditionTypes;
import cbs.dsl.api.ConditionTypes.ConditionOutput;
import cbs.dsl.api.DefinitionRegistry;
import cbs.dsl.api.context.ConditionContext;
import cbs.dsl.api.context.ConditionEvaluator;
import cbs.dsl.builder.ConditionDslObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Evaluates a {@link ConditionDslObject} DSL descriptor at runtime.
 *
 * <p>Interprets the check block directly. Holds a reference to the {@link DefinitionRegistry} so
 * that nested condition resolution can be performed.
 */
@RequiredArgsConstructor
public class RegistryConditionEvaluator implements ConditionEvaluator {

  private final DefinitionRegistry registry;

  /**
   * Resolves a condition definition by code from the registry.
   *
   * @param code the condition code
   * @return the condition definition
   * @throws IllegalArgumentException if not found
   */
  @NonNull
  public ConditionDefinition resolveCondition(@NonNull String code) {
    return registry.resolveCondition(code);
  }

  /**
   * Evaluates the check block of the given condition DSL object.
   *
   * @param dsl the condition DSL object
   * @param ctx the condition context
   * @return the condition context result
   */
  public ConditionContext evaluateCheck(
      @NonNull ConditionDslObject dsl, @NonNull ConditionContext ctx) {
    var result = dsl.checkBlock().apply(ctx);
    if (result instanceof ConditionContext cctx) {
      return cctx.copy();
    }
    return ctx.copy();
  }

  @Override
  public ConditionOutput evaluate(String code, Map<String, Object> params) {
    return resolveCondition(code).check(ConditionTypes.ConditionInput.from(params));
  }
}
