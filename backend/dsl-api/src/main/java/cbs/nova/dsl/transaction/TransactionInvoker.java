package cbs.nova.dsl.transaction;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.process.ProcessContext;
import cbs.nova.dsl.Result;
import org.jspecify.annotations.NonNull;

/**
 * Pluggable transaction execution strategy. When a process context is configured with
 * {@link TransactionRouting#TEMPORAL_ACTIVITY},
 * {@link ProcessContext#runTransaction(String, Object)} will delegate to the registered invoker
 * instead of calling the local transaction runner. This lets generated Temporal workflows route
 * transaction calls to activity stubs while keeping the DSL runtime free of Temporal SDK
 * dependencies.
 */
public interface TransactionInvoker {

  @NonNull
  Result<?> invoke(@NonNull String name, @NonNull Object input, @NonNull Context<?> ctx);
}
