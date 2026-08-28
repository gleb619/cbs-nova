package cbs.nova.dsl.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.transaction.TransactionExecution;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
  void findByRunIdReturnsSavedExecutionsMostRecentFirst() {
    var repo = new InMemoryTransactionExecutionRepository();
    var a = execution("run-1", "CreateOrder", Map.of("sku", "A"));
    var b = execution("run-1", "ReserveStock", null);
    repo.save(a);
    repo.save(b);

    var found = repo.findByRunId("run-1");

    assertThat(found).containsExactly(b, a);
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

  @Test
  void saveKeepsOnlyMostRecentExecutionsPerRun() {
    var repo = new InMemoryTransactionExecutionRepository();
    for (int i = 1; i <= 101; i++) {
      repo.save(execution("run-1", "step-" + i, null));
    }

    var found = repo.findByRunId("run-1");

    assertThat(found).hasSize(100);
    assertThat(found).extracting(TransactionExecution::transactionName)
            .doesNotContain("step-1")
            .startsWith("step-101", "step-100")
            .endsWith("step-3", "step-2");
    assertThat(found.get(0).transactionName()).isEqualTo("step-101");
  }

  @Test
  void retainsOnlyLastHundredTrackedRuns() {
    var repo = new InMemoryTransactionExecutionRepository();
    for (int i = 1; i <= 101; i++) {
      repo.save(execution("run-" + i, "step", null));
    }

    assertThat(repo.findByRunId("run-1")).isEmpty();
    assertThat(repo.findByRunId("run-2")).hasSize(1);
    assertThat(repo.findByRunId("run-101")).hasSize(1);
  }

  @Test
  void evictingParentRunDropsItsTransactionHistory() {
    var executionsRepo = new InMemoryTransactionExecutionRepository();
    var runsRepo = new InMemoryDslRunRepository(executionsRepo::deleteByRunId);

    runsRepo.save(run("run-1"));
    executionsRepo.save(execution("run-1", "CreateOrder", null));
    runsRepo.save(run("run-2"));
    executionsRepo.save(execution("run-2", "CreateOrder", null));
    for (int i = 3; i <= 101; i++) {
      runsRepo.save(run("run-" + i));
    }

    assertThat(runsRepo.findByRunId("run-1")).isEmpty();
    assertThat(executionsRepo.findByRunId("run-1")).isEmpty();
    assertThat(executionsRepo.findByRunId("run-2")).hasSize(1);
    assertThat(runsRepo.findByRunId("run-101")).isPresent();
    assertThat(executionsRepo.findByRunId("run-101")).isEmpty();
  }

  @Test
  void concurrentSavesPreserveInvariants() throws Exception {
    var repo = new InMemoryTransactionExecutionRepository();
    int threads = 8;
    int savesPerThread = 500;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    AtomicInteger saved = new AtomicInteger();
    try {
      for (int t = 0; t < threads; t++) {
        int threadIndex = t;
        pool.submit(() -> {
          for (int i = 0; i < savesPerThread; i++) {
            repo.save(execution("run-" + (threadIndex * savesPerThread + i), "step",
                    null));
            saved.incrementAndGet();
          }
        });
      }
    } finally {
      pool.shutdown();
    }
    assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    assertThat(saved.get()).isEqualTo(threads * savesPerThread);

    // Every run is either fully tracked or fully evicted (never partially present), and the
    // number of tracked runs never exceeds CAPACITY once the churn settles.
    int tracked = 0;
    for (int t = 0; t < threads; t++) {
      for (int i = 0; i < savesPerThread; i++) {
        String runId = "run-" + (t * savesPerThread + i);
        var found = repo.findByRunId(runId);
        assertThat(found.size()).isIn(0, 1);
        tracked += found.size();
      }
    }
    assertThat(tracked).isBetween(1, 100);
  }

  @Test
  void concurrentSaveAndDeleteForSameRunLeaveConsistentState() throws Exception {
    var repo = new InMemoryTransactionExecutionRepository();
    int threads = 4;
    int rounds = 2000;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int t = 0; t < threads; t++) {
        int threadIndex = t;
        futures.add(pool.submit(() -> {
          for (int i = 0; i < rounds; i++) {
            repo.save(execution("run-shared", "step-" + threadIndex + "-" + i, null));
            if (i % 3 == 0) {
              repo.deleteByRunId("run-shared");
            }
            repo.findByRunId("run-shared");
          }
        }));
      }
    } finally {
      pool.shutdown();
    }
    assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    for (Future<?> future : futures) {
      future.get();
    }

    assertThat(repo.findByRunId("run-shared").size()).isLessThanOrEqualTo(100);

    repo.deleteByRunId("run-shared");
    assertThat(repo.findByRunId("run-shared")).isEmpty();
  }

  private static DslRun run(String runId) {
    return DslRun.builder()
            .runId(runId)
            .processName("OrderProcess")
            .status(DslRunStatus.RUNNING.name())
            .startedAt(EXECUTED_AT)
            .build();
  }
}
