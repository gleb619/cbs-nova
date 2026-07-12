package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

/**
 * Pluggable transaction execution strategy. When present in process context metadata under
 * {@code dsl.transaction.invoker}, {@link ProcessContext#runTransaction(String, Object)} will
 * delegate to it instead of calling the local transaction runner. This lets generated Temporal
 * workflows route transaction calls to activity stubs while keeping the DSL runtime free of
 * Temporal SDK dependencies.
 */
public interface TransactionInvoker {

  @NonNull
  Result<?> invoke(@NonNull String name, @NonNull Object input, @NonNull Context<?> ctx);
}
