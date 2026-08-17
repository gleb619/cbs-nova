package cbs.nova.dsl.transaction;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.transaction.TransactionDslObject;
import org.jspecify.annotations.NonNull;

public interface CompensationRegistry {

  boolean register(
          @NonNull String transactionName,
          @NonNull String runId,
          @NonNull Context<?> baseCtx,
          @NonNull TransactionDslObject transaction);

  void compensate(
          @NonNull String transactionName,
          @NonNull String runId,
          @NonNull Throwable error,
          @NonNull ContextFactory contextFactory);

  void compensateAll(
          @NonNull String runId,
          @NonNull Throwable error,
          @NonNull ContextFactory contextFactory);

  boolean hasCompensation(@NonNull String runId);

  void clear();
}
