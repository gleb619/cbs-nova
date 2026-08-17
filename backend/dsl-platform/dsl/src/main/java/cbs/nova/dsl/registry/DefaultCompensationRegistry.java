package cbs.nova.dsl.registry;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.transaction.CompensationRegistry;
import cbs.nova.dsl.transaction.CompensationRichContext;
import cbs.nova.dsl.transaction.TransactionDslObject;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DefaultCompensationRegistry implements CompensationRegistry {

  private final Map<String, ConcurrentLinkedDeque<CompensationEntry>> entries = new ConcurrentHashMap<>();

  @Override
  public boolean register(
          @NonNull String transactionName,
          @NonNull String runId,
          @NonNull Context<?> baseCtx,
          @NonNull TransactionDslObject transaction) {
    if (transaction.compensationLogic() == null) {
      return false;
    }
    entries.computeIfAbsent(key(runId), _ -> new ConcurrentLinkedDeque<>())
            .addLast(new CompensationEntry(transactionName, transaction, baseCtx));
    return true;
  }

  @Override
  public void compensate(
          @NonNull String transactionName,
          @NonNull String runId,
          @NonNull Throwable error,
          @NonNull ContextFactory contextFactory) {
    var deque = entries.get(key(runId));
    if (deque == null) {
      return;
    }
    var iterator = deque.descendingIterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      if (!entry.transactionName.equals(transactionName)) {
        continue;
      }
      if (entry.markFired()) {
        iterator.remove();
        entry.run(error, contextFactory);
        return;
      }
    }
  }

  @Override
  public void compensateAll(
          @NonNull String runId,
          @NonNull Throwable error,
          @NonNull ContextFactory contextFactory) {
    var deque = entries.remove(key(runId));
    if (deque == null) {
      return;
    }
    var iterator = deque.descendingIterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      if (entry.markFired()) {
        entry.run(error, contextFactory);
      }
    }
  }

  @Override
  public boolean hasCompensation(@NonNull String runId) {
    var deque = entries.get(key(runId));
    if (deque == null) {
      return false;
    }
    for (var entry : deque) {
      if (!entry.isFired()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void clear() {
    entries.clear();
  }

  private static String key(String runId) {
    return runId;
  }

  private static final class CompensationEntry {

    final String transactionName;
    final TransactionDslObject transaction;
    final Context<?> baseCtx;
    final AtomicBoolean fired = new AtomicBoolean(false);

    CompensationEntry(
            String transactionName,
            TransactionDslObject transaction,
            Context<?> baseCtx) {
      this.transactionName = transactionName;
      this.transaction = transaction;
      this.baseCtx = baseCtx;
    }

    boolean markFired() {
      return fired.compareAndSet(false, true);
    }

    boolean isFired() {
      return fired.get();
    }

    void run(Throwable error, ContextFactory contextFactory) {
      var compCtx = new CompensationRichContext<>(baseCtx, error, contextFactory);
      transaction.compensationLogic().apply(compCtx);
    }
  }
}
