package cbs.nova.dsl;

import static cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN;

import org.jspecify.annotations.NonNull;

public interface DslObject {

  @NonNull
  String name();

  @NonNull
  DslType type();

  default String description() {
    return EMPTY_MARKDOWN;
  }

  enum DslType {
    PROCESS, TRANSACTION, FUNCTION, OTHER
  }
}
