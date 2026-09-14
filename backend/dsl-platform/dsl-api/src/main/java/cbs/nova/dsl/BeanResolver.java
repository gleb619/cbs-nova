package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

/**
 * Resolves beans by type. The DSL core provides a no-op/default implementation; a Spring-managed
 * runtime replaces it with one backed by the application context.
 */
@FunctionalInterface
public interface BeanResolver {

  @NonNull
  Object resolve(@NonNull Class<?> type);
}
