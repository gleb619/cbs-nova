package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.Set;

public interface ModelRegistry {

  @NonNull
  Set<Class<?>> modelTypes();

  default boolean isRegistered(@NonNull Class<?> type) {
    return modelTypes().contains(type);
  }
}
