package cbs.nova.dsl;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface DescriptionSupport {

  @Nullable
  String description();

}
