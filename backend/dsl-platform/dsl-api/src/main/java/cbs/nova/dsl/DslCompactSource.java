package cbs.nova.dsl;

import static cbs.nova.dsl.config.Constants.JAVA_FILE_SUFFIX;

import java.util.Optional;
import org.jspecify.annotations.NonNull;

import java.util.List;

@FunctionalInterface
public interface DslCompactSource {

  @NonNull
  List<DslObject> define();

  @NonNull
  default String filename() {
    return getClass().getSimpleName() + JAVA_FILE_SUFFIX;
  }

  default Optional<DslObject> byName(String name) {
    return define().stream()
            .filter(dslObject -> dslObject.name().equalsIgnoreCase(name))
            .findFirst();
  }

}
