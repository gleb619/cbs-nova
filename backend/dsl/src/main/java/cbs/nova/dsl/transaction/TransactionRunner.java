package cbs.nova.dsl.transaction;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import org.jspecify.annotations.NonNull;

public interface TransactionRunner {
  @NonNull
  Result<?> run(@NonNull TransactionDslObject transaction, @NonNull Context<?> ctx);
}
