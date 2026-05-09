package cbs.dsl.api;

import cbs.dsl.api.DslComponent.DslComponentModel;
import cbs.dsl.evaluator.Evaluator;

/**
 * Runtime resolver for DSL component instances.
 *
 * <p>When {@link DslComponent#componentModel()} is {@link DslComponentModel#SPRING} (or resolved to
 * it by {@code AUTO}), the generated {@code *Definition} wrapper delegates instantiation to this
 * resolver instead of calling {@code new}. This allows the component to be a full Spring bean with
 * dependency injection, AOP proxies, etc.
 *
 * <p>The framework provides a Spring-aware implementation in the {@code starter} module. Outside a
 * Spring context the resolver is {@code null} and the wrapper falls back to plain construction.
 *
 * @see DslComponent
 * @see DslComponentModel
 */
public interface DslComponentResolver {

  /**
   * Resolve a component instance by its concrete type.
   *
   * @param type the component class (never {@code null})
   * @param <T> the component type
   * @return the resolved instance
   * @throws IllegalArgumentException if the instance cannot be resolved
   */
  <T> T resolve(Class<T> type);

  default Evaluator resolveEvaluator() {
    return resolve(Evaluator.class);
  }

}
