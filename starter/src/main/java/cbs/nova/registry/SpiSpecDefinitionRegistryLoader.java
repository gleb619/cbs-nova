package cbs.nova.registry;

import cbs.dsl.api.DslComponentResolver;
import cbs.dsl.api.SpecDefinitionRegistry;
import cbs.dsl.api.SpecDefinitionRegistryProvider;

import java.util.ServiceLoader;

/**
 * Loads {@link SpecDefinitionRegistryProvider} implementations via SPI and registers them into a
 * {@link SpecDefinitionRegistry}.
 */
public class SpiSpecDefinitionRegistryLoader {

  /**
   * Loads all SPI-discovered providers into the given registry, passing the resolver to each
   * provider so that {@code SPRING} model components can be looked up from the Spring context.
   *
   * @param registry the registry to populate
   * @param resolver the component resolver; may be {@code null}
   */
  public static void loadInto(SpecDefinitionRegistry registry, DslComponentResolver resolver) {
    ServiceLoader.load(SpecDefinitionRegistryProvider.class)
        .forEach(provider -> provider.register(registry, resolver));
  }
}
