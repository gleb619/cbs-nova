package cbs.nova.dsl.registry;

import cbs.nova.dsl.Executable;
import cbs.nova.dsl.function.FunctionDslObject;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Optional;

public interface HelperRegistry {

  void registerHelper(@NonNull String name, @NonNull Executable<?, ?> helper);

  void registerFunction(@NonNull FunctionDslObject function);

  @NonNull
  Optional<Executable<?, ?>> findHelper(@NonNull String name);

  @NonNull
  Optional<FunctionDslObject> findFunction(@NonNull String name);

  boolean containsName(@NonNull String name);

  @NonNull
  Collection<String> allNames();
}
