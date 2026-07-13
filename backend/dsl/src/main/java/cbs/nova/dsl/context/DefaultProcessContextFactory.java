package cbs.nova.dsl.context;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ProcessContextFactory;
import cbs.nova.dsl.SimpleContext;
import cbs.nova.dsl.TransactionRouting;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Default implementation of {@link ProcessContextFactory} that builds {@link SimpleContext}
 * instances.
 */
public final class DefaultProcessContextFactory implements ProcessContextFactory {

  @Override
  public @NonNull Context<?> create(
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId) {
    return new SimpleContext<>(body, metadata, mode, runId);
  }

  @Override
  public @NonNull Context<?> create(
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId,
          @NonNull TransactionRouting transactionRouting) {
    return new SimpleContext<>(body, metadata, mode, runId, transactionRouting);
  }
}
