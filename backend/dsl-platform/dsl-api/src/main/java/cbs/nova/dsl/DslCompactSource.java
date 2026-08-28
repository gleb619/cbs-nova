package cbs.nova.dsl;

import java.util.Optional;
import org.jspecify.annotations.NonNull;

import java.util.List;

@FunctionalInterface
public interface DslCompactSource {

  @NonNull
  List<DslObject> define();

  default Optional<DslObject> byName(String name) {
    return define().stream()
            .filter(dslObject -> dslObject.name().equalsIgnoreCase(name))
            .findFirst();
  }

}
