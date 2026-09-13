package cbs.nova.dsl.model;

import cbs.nova.dsl.DslObject;
import java.util.List;
import org.jspecify.annotations.NonNull;

@FunctionalInterface
public interface ObjectBuilder<T extends DslObject> {

  @NonNull
  T build();

  @NonNull
  default List<DslObject> buildList() {
    return List.of(build());
  }

}
