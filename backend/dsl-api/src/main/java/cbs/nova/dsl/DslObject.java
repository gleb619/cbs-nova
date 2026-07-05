package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public interface DslObject {
  @NonNull
  String name();
  @NonNull
  DslType type();

  enum DslType {
    PROCESS, TRANSACTION, FUNCTION
  }
}
