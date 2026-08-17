package cbs.nova.dsl.registry;

import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.process.ProcessRegistry;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultProcessRegistry implements ProcessRegistry {

  private final ConcurrentHashMap<String, ConcurrentHashMap<String, ProcessDslObject>> store = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ProcessDslObject> latest = new ConcurrentHashMap<>();

  @Override
  public void register(@NonNull ProcessDslObject process) {
    var byVersion = store.computeIfAbsent(process.name(), k -> new ConcurrentHashMap<>());
    if (byVersion.putIfAbsent(process.version(), process) != null) {
      throw new IllegalArgumentException(
              "Process " + process.name() + " version " + process.version()
                      + " is already registered");
    }
    latest.put(process.name(), process);
  }

  @Override
  public @NonNull Optional<ProcessDslObject> find(@NonNull String name) {
    return Optional.ofNullable(latest.get(name));
  }

  @Override
  public @NonNull Optional<ProcessDslObject> find(@NonNull String name, @NonNull String version) {
    return Optional.ofNullable(store.get(name)).map(m -> m.get(version));
  }

  @Override
  public @NonNull Collection<ProcessDslObject> all() {
    return store.values().stream()
            .flatMap(m -> m.values().stream())
            .toList();
  }
}
