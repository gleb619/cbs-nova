package cbs.nova.dsl;

import java.util.Optional;
import org.jspecify.annotations.NonNull;

import java.util.List;

public interface DslDefinitionProvider {

  @NonNull
  List<DslObject> definitions();

  default Optional<DslObject> byName(String name) {
    return definitions().stream()
        .filter(dslObject -> dslObject.name().equalsIgnoreCase(name))
        .findFirst();
  }

}
