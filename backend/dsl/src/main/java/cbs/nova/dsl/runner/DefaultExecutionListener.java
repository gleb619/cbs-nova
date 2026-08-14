package cbs.nova.dsl.runner;

import cbs.nova.dsl.ExecutionListener;
import cbs.nova.dsl.transaction.TransactionExecution;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects successful {@link TransactionExecution}s for a single run to drive compensation history.
 *
 * <p>
 * This class is <strong>per-run by construction</strong>: it holds no shared/static map and is
 * {@code new}-ed once per run (in {@code DefaultProcessRunner.run} and
 * {@code GlobalManager.runProcessWithCompensation}), so it is discarded with the run and can never
 * leak state across runs. Compensation runs below the pipe, inside dispatch, so moving the listener
 * into a pipe stage would risk the dispatch/compensation path not seeing it; the per-run
 * instantiation already provides the run-scoping this collector needs.
 */
public final class DefaultExecutionListener implements ExecutionListener {

  private final List<TransactionExecution> successful = Collections
          .synchronizedList(new ArrayList<>());

  @Override
  public void onTransactionSuccess(@NonNull TransactionExecution execution) {
    successful.add(execution);
  }

  @Override
  public void onTransactionFailure(
          @NonNull String runId,
          @NonNull String transactionName,
          @NonNull Throwable cause) {
  }

  public @NonNull List<TransactionExecution> historyInReverse() {
    synchronized (successful) {
      List<TransactionExecution> copy = new ArrayList<>(successful);
      Collections.reverse(copy);
      return List.copyOf(copy);
    }
  }
}
