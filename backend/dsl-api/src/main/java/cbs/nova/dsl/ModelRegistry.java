package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * SPI contract for publishing the set of model/record types available to the DSL runtime. Generated
 * model registries are discovered via {@link java.util.ServiceLoader} so the runtime can pick
 * type-safe Avaje JSON adapters for Map conversions.
 */
public interface ModelRegistry {

  @NonNull
  Set<Class<?>> modelTypes();

  default boolean isRegistered(@NonNull Class<?> type) {
    return modelTypes().contains(type);
  }
}
