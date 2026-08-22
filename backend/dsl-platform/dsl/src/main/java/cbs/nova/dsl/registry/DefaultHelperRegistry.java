package cbs.nova.dsl.registry;

import cbs.nova.dsl.Executable;
import cbs.nova.dsl.function.FunctionDslObject;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class DefaultHelperRegistry implements HelperRegistry {

  private final ConcurrentHashMap<String, Supplier<Executable<?, ?>>> helperSuppliers = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, FunctionDslObject> functions = new ConcurrentHashMap<>();

  @Override
  public void registerHelper(@NonNull String name, @NonNull Executable<?, ?> helper) {
    if (containsName(name)) {
      throw new IllegalArgumentException("Name already registered: " + name);
    }
    helperSuppliers.put(name, () -> helper);
  }

  @Override
  public void registerHelper(@NonNull String name,
          @NonNull Supplier<Executable<?, ?>> helperSupplier) {
    if (containsName(name)) {
      throw new IllegalArgumentException("Name already registered: " + name);
    }
    helperSuppliers.put(name, helperSupplier);
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
    Supplier<Executable<?, ?>> helperSupplier = helperSuppliers.get(name);
    if (helperSupplier == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(helperSupplier.get());
  }

  @Override
  public @NonNull Optional<FunctionDslObject> findFunction(@NonNull String name) {
    return Optional.ofNullable(functions.get(name));
  }

  @Override
  public boolean containsName(@NonNull String name) {
    return helperSuppliers.containsKey(name) || functions.containsKey(name);
  }

  @Override
  public @NonNull Collection<String> allNames() {
    Set<String> names = new HashSet<>();
    names.addAll(helperSuppliers.keySet());
    names.addAll(functions.keySet());
    return names;
  }

}
