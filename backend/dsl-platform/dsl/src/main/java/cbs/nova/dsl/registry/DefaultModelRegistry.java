package cbs.nova.dsl.registry;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;

@RequiredArgsConstructor
public final class DefaultModelRegistry implements ModelRegistry {

  private final Set<Class<?>> modelTypes;

  public static DefaultModelRegistry discover() {
    return discover(Thread.currentThread().getContextClassLoader());
  }

  public static DefaultModelRegistry discover(ClassLoader classLoader) {
    return new DefaultModelRegistry(loadTypes(classLoader));
  }

  @Override
  public @NonNull Set<Class<?>> modelTypes() {
    return modelTypes;
  }

  private static Set<Class<?>> loadTypes(ClassLoader classLoader) {
    Set<Class<?>> types = new HashSet<>();
    ServiceLoader.load(ModelRegistry.class, classLoader)
            .forEach(provider -> types.addAll(provider.modelTypes()));
    return Collections.unmodifiableSet(types);
  }
}
