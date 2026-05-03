package cbs.nova.config

import cbs.dsl.impl.ImplRegistry
import cbs.dsl.impl.SpiImplRegistryLoader
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean

/**
 * Auto-configuration for DSL implementation registry.
 *
 * Creates a singleton [ImplRegistry] bean and populates it by loading all
 * [cbs.dsl.api.ImplRegistrationProvider] implementations via SPI (ServiceLoader) at application
 * startup.
 */
@AutoConfiguration
class ImplRegistryAutoConfiguration {

  /**
   * Creates and populates the [ImplRegistry] bean.
   *
   * @return the populated registry with all SPI-discovered DSL components
   */
  @Bean
  fun implRegistry(): ImplRegistry {
    val registry = ImplRegistry()
    SpiImplRegistryLoader.loadInto(registry)
    return registry
  }
}
