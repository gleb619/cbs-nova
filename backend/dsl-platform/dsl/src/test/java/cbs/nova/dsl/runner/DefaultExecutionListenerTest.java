package cbs.nova.dsl.runner;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.repository.InMemoryTransactionExecutionRepository;
import cbs.nova.dsl.transaction.TransactionExecution;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultExecutionListenerTest {

  @Test
  void onTransactionSuccessPersistsExecutionIntoRepository() {
    var repo = new InMemoryTransactionExecutionRepository();
    var listener = new DefaultExecutionListener("run-listener", repo);
    var exec = new TransactionExecution("run-listener", "Tx", "in", Instant.now());

    listener.onTransactionSuccess(exec);

    var history = listener.historyInReverse();
    assertThat(history).hasSize(1);
    assertThat(history.get(0)).isSameAs(exec);
    assertThat(repo.findByRunId("run-listener")).hasSize(1);
  }

  @Test
  void historyInReverseIsEmptyForUnknownRun() {
    var listener = new DefaultExecutionListener("missing-run",
            new InMemoryTransactionExecutionRepository());
    assertThat(listener.historyInReverse()).isEmpty();
  }

  @Test
  void historyInReverseSurvivesMultipleSuccesses() {
    var repo = new InMemoryTransactionExecutionRepository();
    var listener = new DefaultExecutionListener("run-multi", repo);
    var e1 = new TransactionExecution("run-multi", "TxA", "in1", Instant.now());
    var e2 = new TransactionExecution("run-multi", "TxB", "in2", Instant.now());
    var e3 = new TransactionExecution("run-multi", "TxC", "in3", Instant.now());

    listener.onTransactionSuccess(e1);
    listener.onTransactionSuccess(e2);
    listener.onTransactionSuccess(e3);

    var history = listener.historyInReverse();
    assertThat(history).hasSize(3);
    // Reverse insertion order — newest first
    assertThat(history.stream().map(TransactionExecution::transactionName).toList())
            .containsExactly("TxC", "TxB", "TxA");
  }

  @Test
  void onTransactionFailureIsNoOpAndDoesNotPolluteHistory() {
    var repo = new InMemoryTransactionExecutionRepository();
    var listener = new DefaultExecutionListener("run-fail", repo);
    // Pre-seed a successful execution so we can assert failure doesn't touch it.
    var ok = new TransactionExecution("run-fail", "TxOk", "in", Instant.now());
    listener.onTransactionSuccess(ok);

    listener.onTransactionFailure("run-fail", "TxBoom", new IllegalStateException("boom"));

    var history = listener.historyInReverse();
    assertThat(history.stream().map(TransactionExecution::transactionName).toList())
            .containsExactly("TxOk");
    // Repository records only successes for this run.
    assertThat(repo.findByRunId("run-fail").stream().map(TransactionExecution::transactionName)
            .toList())
            .containsExactly("TxOk");
  }

  @Test
  void listenerDoesNotCrossContaminateRuns() {
    var repo = new InMemoryTransactionExecutionRepository();
    var runA = new DefaultExecutionListener("run-A", repo);
    var runB = new DefaultExecutionListener("run-B", repo);

    runA.onTransactionSuccess(new TransactionExecution("run-A", "TxA", "in", Instant.now()));
    runB.onTransactionSuccess(new TransactionExecution("run-B", "TxB", "in", Instant.now()));

    var historyA = runA.historyInReverse();
    var historyB = runB.historyInReverse();
    assertThat(historyA.stream().map(TransactionExecution::runId).toList())
            .containsExactly("run-A");
    assertThat(historyB.stream().map(TransactionExecution::runId).toList())
            .containsExactly("run-B");
  }

  @Test
  void emptyHistoryReturnsEmptyListNotNull() {
    var listener = new DefaultExecutionListener("never-run",
            new InMemoryTransactionExecutionRepository());
    List<TransactionExecution> empty = listener.historyInReverse();
    assertThat(empty).isNotNull().isEmpty();
  }
}
