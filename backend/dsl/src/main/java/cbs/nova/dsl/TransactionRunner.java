package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public interface TransactionRunner {
  @NonNull
  Result<?> run(@NonNull TransactionDslObject transaction, @NonNull Context<?> ctx);
}
