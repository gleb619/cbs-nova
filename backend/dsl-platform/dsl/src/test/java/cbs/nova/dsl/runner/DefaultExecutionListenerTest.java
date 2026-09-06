package cbs.nova.dsl.runner;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.repository.InMemoryTransactionExecutionRepository;
import cbs.nova.dsl.transaction.TransactionExecution;
import cbs.nova.dsl.transaction.TransactionExecutionStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultExecutionListenerTest {

  private static TransactionExecution exec(
          String runId,
          String transactionName,
          Object input,
          Instant startedAt,
          Instant finishedAt,
          TransactionExecutionStatus status,
          String error) {
    return new TransactionExecution(
            runId, transactionName, input,
            finishedAt, startedAt, finishedAt, status, error);
  }

  @Test
  void onTransactionSuccessPersistsExecutionIntoRepository() {
    var repo = new InMemoryTransactionExecutionRepository();
    var listener = new DefaultExecutionListener("run-listener", repo);
    var now = Instant.now();
    var exec = exec("run-listener", "Tx", "in", now, now, TransactionExecutionStatus.SUCCESS, null);

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
    var now = Instant.now();
    var e1 = exec("run-multi", "TxA", "in1", now, now, TransactionExecutionStatus.SUCCESS, null);
    var e2 = exec("run-multi", "TxB", "in2", now, now, TransactionExecutionStatus.SUCCESS, null);
    var e3 = exec("run-multi", "TxC", "in3", now, now, TransactionExecutionStatus.SUCCESS, null);

    listener.onTransactionSuccess(e1);
    listener.onTransactionSuccess(e2);
    listener.onTransactionSuccess(e3);

    var history = listener.historyInReverse();
    assertThat(history).hasSize(3);
    assertThat(history.stream().map(TransactionExecution::transactionName).toList())
            .containsExactly("TxC", "TxB", "TxA");
  }

  @Test
  void onTransactionFailureIsNoOpAndDoesNotPolluteHistory() {
    var repo = new InMemoryTransactionExecutionRepository();
    var listener = new DefaultExecutionListener("run-fail", repo);
    var now = Instant.now();
    var ok = exec("run-fail", "TxOk", "in", now, now, TransactionExecutionStatus.SUCCESS, null);
    listener.onTransactionSuccess(ok);

    listener.onTransactionFailure("run-fail", "TxBoom", new IllegalStateException("boom"));

    var history = listener.historyInReverse();
    assertThat(history.stream().map(TransactionExecution::transactionName).toList())
            .containsExactly("TxOk");
    assertThat(repo.findByRunId("run-fail").stream().map(TransactionExecution::transactionName)
            .toList())
            .containsExactly("TxOk");
  }

  @Test
  void listenerDoesNotCrossContaminateRuns() {
    var repo = new InMemoryTransactionExecutionRepository();
    var runA = new DefaultExecutionListener("run-A", repo);
    var runB = new DefaultExecutionListener("run-B", repo);
    var now = Instant.now();

    runA.onTransactionSuccess(exec("run-A", "TxA", "in", now, now, TransactionExecutionStatus.SUCCESS, null));
    runB.onTransactionSuccess(exec("run-B", "TxB", "in", now, now, TransactionExecutionStatus.SUCCESS, null));

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
