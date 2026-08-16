package cbs.nova.dsl.registry;

import cbs.nova.dsl.ModelRegistry;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;

public final class DefaultModelRegistry implements ModelRegistry {

  private final Set<Class<?>> modelTypes;

  public DefaultModelRegistry() {
    this(Thread.currentThread().getContextClassLoader());
  }

  public DefaultModelRegistry(ClassLoader classLoader) {
    Set<Class<?>> types = new HashSet<>();
    ServiceLoader.load(ModelRegistry.class, classLoader)
            .forEach(provider -> types.addAll(provider.modelTypes()));
    this.modelTypes = Collections.unmodifiableSet(types);
  }

  @Override
  public @NonNull Set<Class<?>> modelTypes() {
    return modelTypes;
  }
}
