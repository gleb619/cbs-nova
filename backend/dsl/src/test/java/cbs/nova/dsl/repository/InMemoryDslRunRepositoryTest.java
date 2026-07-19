package cbs.nova.dsl.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DslRun;
import cbs.nova.dsl.DslRunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

class InMemoryDslRunRepositoryTest {

  private static final Instant STARTED_AT = Instant.parse("2026-07-19T00:00:00Z");

  private static DslRun run(String runId, String processName, String status) {
    return DslRun.builder()
            .runId(runId)
            .processName(processName)
            .status(status)
            .startedAt(STARTED_AT)
            .build();
  }

  @Test
  void saveRoundTripsThroughFindByRunId() {
    var repo = new InMemoryDslRunRepository();
    var run = run("run-1", "OrderProcess", DslRunStatus.RUNNING.name());

    var saved = repo.save(run);

    assertThat(saved).isSameAs(run);
    assertThat(repo.findByRunId("run-1")).containsSame(run);
  }

  @Test
  void findByRunIdReturnsEmptyForUnknownRunId() {
    var repo = new InMemoryDslRunRepository();

    assertThat(repo.findByRunId("missing")).isEmpty();
  }

  @Test
  void findByProcessNameReturnsOnlyMatchingRuns() {
    var repo = new InMemoryDslRunRepository();
    repo.save(run("run-1", "OrderProcess", DslRunStatus.RUNNING.name()));
    repo.save(run("run-2", "InvoiceProcess", DslRunStatus.RUNNING.name()));

    var orderRuns = repo.findByProcessName("OrderProcess");

    assertThat(orderRuns).hasSize(1);
    assertThat(orderRuns.get(0).runId()).isEqualTo("run-1");
  }

  @Test
  void findByProcessNameReturnsEmptyWhenNoRunsMatch() {
    var repo = new InMemoryDslRunRepository();
    repo.save(run("run-1", "OrderProcess", DslRunStatus.RUNNING.name()));

    assertThat(repo.findByProcessName("UnknownProcess")).isEmpty();
  }

  @Test
  void findByProcessNameReturnsMultipleRunsForSharedProcessName() {
    var repo = new InMemoryDslRunRepository();
    var runA = run("run-a", "OrderProcess", DslRunStatus.RUNNING.name());
    var runB = run("run-b", "OrderProcess", DslRunStatus.RUNNING.name());
    repo.save(runA);
    repo.save(runB);

    assertThat(repo.findByProcessName("OrderProcess"))
            .containsExactlyInAnyOrder(runA, runB);
  }

  @Test
  void findByProcessNameDoesNotBleedAcrossProcesses() {
    var repo = new InMemoryDslRunRepository();
    repo.save(run("run-1", "OrderProcess", DslRunStatus.RUNNING.name()));
    repo.save(run("run-2", "InvoiceProcess", DslRunStatus.RUNNING.name()));

    assertThat(repo.findByProcessName("InvoiceProcess"))
            .extracting(DslRun::runId)
            .containsExactly("run-2");
    assertThat(repo.findByProcessName("OrderProcess"))
            .extracting(DslRun::runId)
            .containsExactly("run-1");
  }

  @Test
  void saveWithSameRunIdOverwritesPreviousRun() {
    var repo = new InMemoryDslRunRepository();
    var running = run("run-1", "OrderProcess", DslRunStatus.RUNNING.name());
    var completed = run("run-1", "OrderProcess", DslRunStatus.COMPLETED.name());

    repo.save(running);
    repo.save(completed);

    assertThat(repo.findByRunId("run-1"))
            .get()
            .extracting(DslRun::status)
            .isEqualTo(DslRunStatus.COMPLETED.name());
    assertThat(repo.findByProcessName("OrderProcess")).hasSize(1);
  }

  @Test
  void findByProcessNameReturnsIsolatedCopy() {
    var repo = new InMemoryDslRunRepository();
    var run = run("run-1", "OrderProcess", DslRunStatus.RUNNING.name());
    repo.save(run);

    var snapshot = repo.findByProcessName("OrderProcess");
    snapshot.add(run("run-2", "OrderProcess", DslRunStatus.RUNNING.name()));

    assertThat(repo.findByProcessName("OrderProcess"))
            .hasSize(1)
            .containsExactly(run);
  }
}
