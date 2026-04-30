package cbs.nova.config;

import cbs.nova.registry.DslRegistry;
import cbs.nova.registry.SpiImplRegistryLoader;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for DSL implementation registry.
 *
 * <p>Creates a singleton {@link DslRegistry} bean and populates it by loading all
 * {@link cbs.dsl.api.ImplRegistrationProvider} implementations via SPI (ServiceLoader) at
 * application startup.
 */
@AutoConfiguration
public class ImplRegistryAutoConfiguration {

  /**
   * Creates and populates the {@link DslRegistry} bean.
   *
   * @return the populated registry with all SPI-discovered DSL components
   */
  @Bean
  public DslRegistry dslRegistry() {
    DslRegistry registry = new DslRegistry();
    SpiImplRegistryLoader.loadInto(registry);
    return registry;
  }
}
