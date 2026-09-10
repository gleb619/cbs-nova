package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public interface DslObject {

  @NonNull
  String name();

  @NonNull
  DslType type();

  default String description() {
    return "<!-- NONE -->";
  }

  enum DslType {
    PROCESS, TRANSACTION, FUNCTION, OTHER
  }
}
