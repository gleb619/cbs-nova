package cbs.nova.config;

import cbs.dsl.api.DefinitionRegistry;
import cbs.dsl.evaluator.Evaluator;
import cbs.dsl.evaluator.RegistryEventEvaluator;
import cbs.dsl.evaluator.RegistryHelperEvaluator;
import cbs.nova.registry.DslRegistry;
import cbs.nova.registry.SpiImplRegistryLoader;
import cbs.nova.registry.SpringDslComponentResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for DSL registry and evaluator beans.
 *
 * <p>Creates a singleton {@link DefinitionRegistry} bean and populates it by loading all
 * {@link cbs.dsl.api.DefinitionRegistryProvider} implementations via SPI (ServiceLoader) at
 * application startup. When a {@link SpringDslComponentResolver} is available it is injected into
 * the registry so that {@code SPRING} model components are resolved from the Spring context.
 *
 * <p>Also creates the {@link RegistryHelperEvaluator} and {@link Evaluator} beans so that generated
 * wrappers can resolve them from the Spring context.
 */
@AutoConfiguration
public class DslRegistryConfiguration {

  /**
   * Creates and populates the {@link DefinitionRegistry} bean.
   *
   * <p>If a {@link SpringDslComponentResolver} is present in the context, it is wired into the
   * registry and passed to SPI providers so generated wrappers can look up Spring-managed beans.
   *
   * @param resolver the Spring component resolver; may be {@code null} if Spring context is not
   *     available
   * @return the populated registry with all SPI-discovered DSL components
   */
  @Bean
  public DefinitionRegistry definitionRegistry(SpringDslComponentResolver resolver) {
    DslRegistry registry = new DslRegistry();
    registry.setComponentResolver(resolver);
    SpiImplRegistryLoader.loadInto(registry, resolver);
    return registry;
  }

  @Bean
  public RegistryEventEvaluator eventEvaluator(DefinitionRegistry registry) {
    return new RegistryEventEvaluator(registry);
  }

  /**
   * Creates the {@link RegistryHelperEvaluator} bean backed by the {@link DefinitionRegistry}.
   *
   * @param registry the definition registry
   * @return the helper evaluator
   */
  @Bean
  public RegistryHelperEvaluator helperEvaluator(DefinitionRegistry registry) {
    return new RegistryHelperEvaluator(registry);
  }

  /**
   * Creates the {@link Evaluator} bean that wraps the {@link RegistryHelperEvaluator}.
   *
   * @param helperEvaluator the helper evaluator
   * @return the evaluator
   */
  @Bean
  public Evaluator evaluator(
      RegistryEventEvaluator eventEvaluator, RegistryHelperEvaluator helperEvaluator) {
    return new Evaluator(eventEvaluator, helperEvaluator);
  }
}
