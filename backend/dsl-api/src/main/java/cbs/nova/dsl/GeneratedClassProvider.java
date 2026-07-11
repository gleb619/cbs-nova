package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

/**
 * SPI contract published by dsl-codegen so generated Temporal workflow/activity classes can be
 * discovered by name at runtime instead of being imported directly.
 */
public interface GeneratedClassProvider {

  @NonNull
  GeneratedClassDescriptor descriptor();
}
