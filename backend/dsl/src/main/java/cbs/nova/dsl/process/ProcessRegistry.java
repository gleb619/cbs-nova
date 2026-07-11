package cbs.nova.dsl.process;

import java.util.Collection;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

public interface ProcessRegistry {
  void register(@NonNull ProcessDslObject process);

  @NonNull
  Optional<ProcessDslObject> find(@NonNull String name);

  @NonNull
  Collection<ProcessDslObject> all();
}
