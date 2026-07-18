package cbs.nova.dsl;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.transaction.TransactionDslObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class CompensationRegistry {

  private final Map<String, List<CompensationEntry>> entries = new ConcurrentHashMap<>();

  public boolean register(
          @NonNull String transactionName,
          @NonNull String runId,
          @NonNull Context<?> baseCtx,
          @NonNull TransactionDslObject transaction) {
    if (transaction.compensationLogic() == null) {
      return false;
    }
    entries.computeIfAbsent(key(runId), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(new CompensationEntry(transactionName, transaction, baseCtx));
    return true;
  }

  public void compensate(
          @NonNull String transactionName,
          @NonNull String runId,
          @NonNull Throwable error,
          @NonNull ExecutionTraceCollector traceCollector,
          @NonNull ContextFactory contextFactory) {
    var list = entries.get(key(runId));
    if (list == null) {
      return;
    }
    synchronized (list) {
      for (int i = list.size() - 1; i >= 0; i--) {
        var entry = list.get(i);
        if (entry.transactionName().equals(transactionName)) {
          list.remove(i);
          entry.run(error, traceCollector, contextFactory);
          return;
        }
      }
    }
  }

  public void compensateAll(
          @NonNull String runId,
          @NonNull Throwable error,
          @NonNull ExecutionTraceCollector traceCollector,
          @NonNull ContextFactory contextFactory) {
    var list = entries.remove(key(runId));
    if (list == null) {
      return;
    }
    synchronized (list) {
      for (int i = list.size() - 1; i >= 0; i--) {
        list.get(i).run(error, traceCollector, contextFactory);
      }
    }
  }

  public boolean hasCompensation(@NonNull String runId) {
    var list = entries.get(key(runId));
    return list != null && !list.isEmpty();
  }

  public void clear() {
    entries.clear();
  }

  private static String key(String runId) {
    return runId;
  }

  private record CompensationEntry(
          String transactionName,
          TransactionDslObject transaction,
          Context<?> baseCtx) {

    void run(
            Throwable error,
            ExecutionTraceCollector traceCollector,
            ContextFactory contextFactory) {
      var compCtx = new CompensationRichContext<>(baseCtx, error, traceCollector,
              contextFactory);
      transaction.compensationLogic().apply(compCtx);
    }
  }
}
