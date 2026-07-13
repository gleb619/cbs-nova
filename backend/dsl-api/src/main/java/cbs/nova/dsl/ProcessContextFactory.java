package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Factory responsible for creating {@link Context} instances used by DSL processes.
 */
public interface ProcessContextFactory {

  @NonNull
  Context<?> create(
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId);

  @NonNull
  Context<?> create(
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId,
          @NonNull TransactionRouting transactionRouting);
}
