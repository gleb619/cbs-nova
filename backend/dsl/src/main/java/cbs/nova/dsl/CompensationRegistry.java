package cbs.nova.dsl;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.transaction.TransactionDslObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Run-scoped registry of transaction compensations. A compensation is registered before a
 * transaction runs (keyed by transaction name and run id) and can be invoked later without the
 * caller repeating the {@code find/if-null} boilerplate.
 */
@RequiredArgsConstructor
public final class CompensationRegistry {

  private final Map<String, CompensationEntry> entries = new ConcurrentHashMap<>();

  public boolean register(
          @NonNull String transactionName,
          @NonNull String runId,
          @NonNull Context<?> baseCtx,
          @NonNull TransactionDslObject transaction) {
    if (transaction.compensationLogic() == null) {
      return false;
    }
    entries.put(key(transactionName, runId), new CompensationEntry(transaction, baseCtx));
    return true;
  }

  public void compensate(
          @NonNull String transactionName,
          @NonNull String runId,
          @NonNull Throwable error,
          @NonNull ExecutionTraceCollector traceCollector,
          @NonNull ContextFactory contextFactory) {
    var entry = entries.remove(key(transactionName, runId));
    if (entry == null) {
      return;
    }
    var compCtx = new CompensationRichContext<>(entry.baseCtx(), error, traceCollector,
            contextFactory);
    entry.transaction().compensationLogic().apply(compCtx);
  }

  public boolean hasCompensation(@NonNull String transactionName, @NonNull String runId) {
    return entries.containsKey(key(transactionName, runId));
  }

  public void clear() {
    entries.clear();
  }

  private static String key(String transactionName, String runId) {
    return transactionName + "#" + runId;
  }

  private record CompensationEntry(TransactionDslObject transaction, Context<?> baseCtx) {
  }
}
