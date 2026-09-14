package cbs.nova.dsl.model;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface ObjectDescriptor {

  @NonNull
  String name();

  @NonNull
  DslType type();

  // TODO: fix, remove `default` modifier
  default @Nullable String description() {
    return null;
  }

  @Nullable
  Class<?> inputType();

  @Nullable
  Class<?> outputType();

  default DslDescriptor toDslDescriptor() {
    return DslDescriptor.builder()
            .objectDescriptor(this)
            .build();
  }

}
