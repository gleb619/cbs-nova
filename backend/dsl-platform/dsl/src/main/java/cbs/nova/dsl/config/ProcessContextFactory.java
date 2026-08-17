package cbs.nova.dsl.config;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.SimpleContext;
import cbs.nova.dsl.transaction.TransactionRouting;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public final class ProcessContextFactory {

  public @NonNull Context<?> create(
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId) {
    return new SimpleContext<>(body, metadata, mode, runId, TransactionRouting.LOCAL,
            null, null, null);
  }

  public @NonNull Context<?> create(
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId,
          @NonNull TransactionRouting transactionRouting) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting,
            null, null, null);
  }
}
