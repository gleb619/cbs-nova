package cbs.nova.dsl.transaction;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.process.ProcessContext;
import org.jspecify.annotations.NonNull;

public interface TransactionInvoker {

  @NonNull
  Result<?> invoke(@NonNull String name, @NonNull Object input, @NonNull Context<?> ctx);
}
