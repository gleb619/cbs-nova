package cbs.nova.dsl.registry;

import cbs.nova.dsl.ModelRegistry;
import org.jspecify.annotations.NonNull;

import java.util.Set;

public final class FakeModelRegistry implements ModelRegistry {

  @Override
  public @NonNull Set<Class<?>> modelTypes() {
    return Set.of(String.class);
  }
}
