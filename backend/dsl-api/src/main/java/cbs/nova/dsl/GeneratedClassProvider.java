package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public interface GeneratedClassProvider {

  @NonNull
  GeneratedClassDescriptor descriptor();

  default @NonNull String executeJson() {
    return descriptor().executeJson();
  }

  default Object implementationInstance() {
    throw new UnsupportedOperationException(
            "implementationInstance() is only provided by generated class providers");
  }
}
