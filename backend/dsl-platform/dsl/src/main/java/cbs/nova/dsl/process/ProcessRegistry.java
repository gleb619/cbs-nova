package cbs.nova.dsl.process;

import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Optional;

public interface ProcessRegistry {

  void register(@NonNull ProcessDslObject process);

  @NonNull
  Optional<ProcessDslObject> find(@NonNull String name);

  @NonNull
  default Optional<ProcessDslObject> find(@NonNull String name, @NonNull String version) {
    return find(name);
  }

  @NonNull
  Collection<ProcessDslObject> all();
}
