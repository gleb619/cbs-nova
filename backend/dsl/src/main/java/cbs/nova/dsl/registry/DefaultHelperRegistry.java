package cbs.nova.dsl.registry;

import cbs.nova.dsl.Executable;
import cbs.nova.dsl.function.FunctionDslObject;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NonNull;

public final class DefaultHelperRegistry implements HelperRegistry {

  private final ConcurrentHashMap<String, Executable<?, ?>> helpers = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, FunctionDslObject> functions = new ConcurrentHashMap<>();

  @Override
  public void registerHelper(@NonNull String name, @NonNull Executable<?, ?> helper) {
    if (containsName(name)) {
      throw new IllegalArgumentException("Name already registered: " + name);
    }
    helpers.put(name, helper);
  }

  @Override
  public void registerFunction(@NonNull FunctionDslObject function) {
    if (containsName(function.name())) {
      throw new IllegalArgumentException("Name already registered: " + function.name());
    }
    functions.put(function.name(), function);
  }

  @Override
  public @NonNull Optional<Executable<?, ?>> findHelper(@NonNull String name) {
    return Optional.ofNullable(helpers.get(name));
  }

  @Override
  public @NonNull Optional<FunctionDslObject> findFunction(@NonNull String name) {
    return Optional.ofNullable(functions.get(name));
  }

  @Override
  public boolean containsName(@NonNull String name) {
    return helpers.containsKey(name) || functions.containsKey(name);
  }

  @Override
  public @NonNull Collection<String> allNames() {
    Set<String> names = new HashSet<>();
    names.addAll(helpers.keySet());
    names.addAll(functions.keySet());
    return names;
  }
}
