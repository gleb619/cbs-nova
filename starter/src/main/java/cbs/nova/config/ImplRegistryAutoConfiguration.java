package cbs.nova.config;

import cbs.dsl.api.DefinitionRegistryProvider;
import cbs.nova.registry.DslRegistry;
import cbs.nova.registry.SpiImplRegistryLoader;
import cbs.nova.registry.SpringDslComponentResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for DSL implementation registry.
 *
 * <p>Creates a singleton {@link DslRegistry} bean and populates it by loading all
 * {@link DefinitionRegistryProvider} implementations via SPI (ServiceLoader) at
 * application startup. When a {@link SpringDslComponentResolver} is available it is injected into
 * the registry so that {@code SPRING} model components are resolved from the Spring context.
 */
@AutoConfiguration
public class ImplRegistryAutoConfiguration {

  /**
   * Creates and populates the {@link DslRegistry} bean.
   *
   * <p>If a {@link SpringDslComponentResolver} is present in the context, it is wired into the
   * registry and passed to SPI providers so generated wrappers can look up Spring-managed beans.
   *
   * @param resolver the Spring component resolver; may be {@code null} if Spring context is not
   *     available
   * @return the populated registry with all SPI-discovered DSL components
   */
  @Bean
  public DslRegistry dslRegistry(SpringDslComponentResolver resolver) {
    DslRegistry registry = new DslRegistry();
    registry.setComponentResolver(resolver);
    SpiImplRegistryLoader.loadInto(registry, resolver);
    return registry;
  }
}
