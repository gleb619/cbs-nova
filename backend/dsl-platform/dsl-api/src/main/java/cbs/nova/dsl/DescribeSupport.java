package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

@FunctionalInterface
public interface DescribeSupport {

  @NonNull
  ExecutableDescriptor describe();

}
