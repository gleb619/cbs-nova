package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultProcessRegistry implements ProcessRegistry {
  private final ConcurrentHashMap<String, ProcessDslObject> store = new ConcurrentHashMap<>();

  @Override
  public void register(@NonNull ProcessDslObject process) {
    if (store.putIfAbsent(process.name(), process) != null) {
      throw new IllegalArgumentException("Process already registered: " + process.name());
    }
  }

  @Override
  public @NonNull Optional<ProcessDslObject> find(@NonNull String name) {
    return Optional.ofNullable(store.get(name));
  }

  @Override
  public @NonNull Collection<ProcessDslObject> all() {
    return store.values();
  }
}
