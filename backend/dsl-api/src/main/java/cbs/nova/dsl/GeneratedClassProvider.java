package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

/**
 * SPI contract published by dsl-codegen so generated Temporal workflow/activity classes can be
 * discovered by name at runtime instead of being imported directly.
 */
public interface GeneratedClassProvider {

  @NonNull
  GeneratedClassDescriptor descriptor();

  /**
   * Returns the JSON serialized AST of the original DSL {@code .execute(...)} body.
   */
  default @NonNull String executeJson() {
    return descriptor().executeJson();
  }

  /**
   * Returns a new instance of the generated Temporal implementation class. Generated providers for
   * transactions override this so the worker can register the activity object without reflection.
   */
  default Object implementationInstance() {
    throw new UnsupportedOperationException(
            "implementationInstance() is only provided by generated class providers");
  }
}
