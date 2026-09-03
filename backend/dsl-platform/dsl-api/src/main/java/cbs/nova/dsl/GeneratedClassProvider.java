package cbs.nova.dsl;

import static cbs.nova.dsl.model.EmptyDslObject.emptyDslObject;

import org.jspecify.annotations.NonNull;

public interface GeneratedClassProvider {

  @NonNull
  GeneratedClassDescriptor descriptor();

  default @NonNull String executeJson() {
    return descriptor().executeJson();
  }

  @NonNull
  default Object implementationInstance() {
    throw new UnsupportedOperationException(
            "implementationInstance() is only provided by generated class providers");
  }

  @NonNull
  default DslObject dslObject() {
    return emptyDslObject();
  }

}
