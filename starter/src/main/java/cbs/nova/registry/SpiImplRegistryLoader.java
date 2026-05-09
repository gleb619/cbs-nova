package cbs.nova.registry;

import cbs.dsl.api.DefinitionRegistry;
import cbs.dsl.api.DefinitionRegistryProvider;
import cbs.dsl.api.DslComponentResolver;

import java.util.ServiceLoader;

/**
 * Loads {@link DefinitionRegistryProvider} implementations via SPI and registers them into a
 * {@link DefinitionRegistry}.
 */
public class SpiImplRegistryLoader {

  /**
   * Loads all SPI-discovered providers into the given registry, passing the resolver to each
   * provider so that {@code SPRING} model components can be looked up from the Spring context.
   *
   * @param registry the registry to populate
   * @param resolver the component resolver; may be {@code null}
   */
  public static void loadInto(DefinitionRegistry registry, DslComponentResolver resolver) {
    ServiceLoader.load(DefinitionRegistryProvider.class)
        .forEach(provider -> provider.register(registry, resolver));
  }
}
