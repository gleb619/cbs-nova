package cbs.nova.dsl.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.transaction.TransactionExecution;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

class InMemoryTransactionExecutionRepositoryTest {

  private static final Instant EXECUTED_AT = Instant.parse("2026-07-19T00:00:00Z");

  private static TransactionExecution execution(String runId, String transactionName,
          Object input) {
    return new TransactionExecution(runId, transactionName, input, EXECUTED_AT);
  }

  @Test
  void saveReturnsSameExecution() {
    var repo = new InMemoryTransactionExecutionRepository();
    var execution = execution("run-1", "CreateOrder", Map.of("sku", "ABC"));

    var saved = repo.save(execution);

    assertThat(saved).isSameAs(execution);
  }

  @Test
  void findByRunIdReturnsSavedExecutions() {
    var repo = new InMemoryTransactionExecutionRepository();
    var a = execution("run-1", "CreateOrder", Map.of("sku", "A"));
    var b = execution("run-1", "ReserveStock", null);
    repo.save(a);
    repo.save(b);

    var found = repo.findByRunId("run-1");

    assertThat(found).containsExactly(a, b);
  }

  @Test
  void findByRunIdReturnsEmptyForUnknownRunId() {
    var repo = new InMemoryTransactionExecutionRepository();

    assertThat(repo.findByRunId("missing")).isEmpty();
  }

  @Test
  void findByRunIdDoesNotBleedAcrossRunIds() {
    var repo = new InMemoryTransactionExecutionRepository();
    repo.save(execution("run-1", "CreateOrder", null));
    repo.save(execution("run-2", "CreateOrder", null));

    assertThat(repo.findByRunId("run-1")).hasSize(1);
    assertThat(repo.findByRunId("run-2")).hasSize(1);
  }

  @Test
  void deleteByRunIdRemovesExecutionsForRunId() {
    var repo = new InMemoryTransactionExecutionRepository();
    repo.save(execution("run-1", "CreateOrder", null));
    repo.save(execution("run-1", "ReserveStock", null));
    repo.save(execution("run-2", "CreateOrder", null));

    repo.deleteByRunId("run-1");

    assertThat(repo.findByRunId("run-1")).isEmpty();
    assertThat(repo.findByRunId("run-2")).hasSize(1);
  }

  @Test
  void deleteByRunIdIsNoOpForUnknownRunId() {
    var repo = new InMemoryTransactionExecutionRepository();

    repo.deleteByRunId("missing");

    assertThat(repo.findByRunId("missing")).isEmpty();
  }

  @Test
  void findByRunIdReturnsIsolatedCopy() {
    var repo = new InMemoryTransactionExecutionRepository();
    var execution = execution("run-1", "CreateOrder", null);
    repo.save(execution);

    var snapshot = repo.findByRunId("run-1");
    snapshot.add(execution("run-1", "ReserveStock", null));

    assertThat(repo.findByRunId("run-1")).hasSize(1);
  }
}
