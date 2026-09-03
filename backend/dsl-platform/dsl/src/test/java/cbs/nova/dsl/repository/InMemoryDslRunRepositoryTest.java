package cbs.nova.dsl.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunSearchResult;
import cbs.nova.dsl.history.DslRunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

  @Test
  void retainsOnlyLastOneHundredSavedRuns() {
    var repo = new InMemoryDslRunRepository();
    for (int i = 1; i <= 101; i++) {
      repo.save(run("run-" + i, "OrderProcess", DslRunStatus.RUNNING.name()));
    }

    assertThat(repo.findByRunId("run-1")).isEmpty();
    assertThat(repo.findByRunId("run-2")).isPresent();
    assertThat(repo.findByRunId("run-101")).isPresent();
    assertThat(repo.findByProcessName("OrderProcess")).hasSize(100);
  }

  @Test
  void updatingExistingRunDoesNotChangeEvictionOrder() {
    var repo = new InMemoryDslRunRepository();
    for (int i = 1; i <= 100; i++) {
      repo.save(run("run-" + i, "OrderProcess", DslRunStatus.RUNNING.name()));
    }

    repo.updateFinished(
            "run-1",
            DslRunStatus.COMPLETED.name(),
            null,
            null,
            Instant.now(),
            null);
    repo.save(run("run-101", "OrderProcess", DslRunStatus.RUNNING.name()));

    assertThat(repo.findByRunId("run-1")).isEmpty();
    assertThat(repo.findByRunId("run-2")).isPresent();
    assertThat(repo.findByRunId("run-101")).isPresent();
  }

  @Test
  void updateFinishedIfRunningIsNoOpWhenRunAlreadyTerminal() {
    var repo = new InMemoryDslRunRepository();
    repo.save(run("run-1", "OrderProcess", DslRunStatus.COMPLETED.name()));

    int affected = repo.updateFinishedIfRunning(
            "run-1",
            DslRunStatus.STALE.name(),
            null,
            null,
            Instant.now(),
            null);

    assertThat(affected).isZero();
    assertThat(repo.findByRunId("run-1"))
            .get()
            .extracting(DslRun::status)
            .isEqualTo(DslRunStatus.COMPLETED.name());
  }

  @Test
  void updateFinishedIfRunningTransitionsRunningRun() {
    var repo = new InMemoryDslRunRepository();
    repo.save(run("run-1", "OrderProcess", DslRunStatus.RUNNING.name()));

    int affected = repo.updateFinishedIfRunning(
            "run-1",
            DslRunStatus.STALE.name(),
            null,
            null,
            Instant.now(),
            null);

    assertThat(affected).isEqualTo(1);
    assertThat(repo.findByRunId("run-1"))
            .get()
            .extracting(DslRun::status)
            .isEqualTo(DslRunStatus.STALE.name());
  }

  @Test
  void updateFinishedIfRunningIsNoOpWhenRunMissing() {
    var repo = new InMemoryDslRunRepository();

    int affected = repo.updateFinishedIfRunning(
            "missing",
            DslRunStatus.STALE.name(),
            null,
            null,
            Instant.now(),
            null);

    assertThat(affected).isZero();
  }

  @Test
  void evictedRunsAreRemovedFromKnownProcessNames() {
    var repo = new InMemoryDslRunRepository();
    repo.save(run("old-a", "OrderProcess", DslRunStatus.RUNNING.name()));
    for (int i = 1; i <= 100; i++) {
      repo.save(run("run-" + i, "InvoiceProcess", DslRunStatus.RUNNING.name()));
    }

    assertThat(repo.knownProcessNames())
            .doesNotContain("OrderProcess")
            .contains("InvoiceProcess");
  }

  @Test
  void evictionHookReceivesEvictedRunId() {
    List<String> evicted = new ArrayList<>();
    var repo = new InMemoryDslRunRepository(evicted::add);
    for (int i = 1; i <= 101; i++) {
      repo.save(run("run-" + i, "OrderProcess", DslRunStatus.RUNNING.name()));
    }

    assertThat(evicted).containsExactly("run-1");
  }

  private static DslRun run(String runId, String processName, String status, Instant startedAt,
          String mode) {
    return DslRun.builder()
            .runId(runId)
            .processName(processName)
            .status(status)
            .startedAt(startedAt)
            .executionMode(mode)
            .build();
  }

  @Test
  void searchWithNoFiltersReturnsAllRunsOrderedByStartedAtDesc() {
    var repo = new InMemoryDslRunRepository();
    Instant t1 = Instant.parse("2026-08-13T10:00:00Z");
    Instant t2 = Instant.parse("2026-08-13T10:01:00Z");
    repo.save(run("run-1", "OrderProcess", DslRunStatus.COMPLETED.name(), t1, "RUN"));
    repo.save(run("run-2", "OrderProcess", DslRunStatus.RUNNING.name(), t2, "RUN"));

    DslRunSearchResult result = repo.search(null, null, null, null, 0, 10);

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).extracting(DslRun::runId).containsExactly("run-2", "run-1");
  }

  @Test
  void searchFiltersByProcessNameCaseSensitively() {
    var repo = new InMemoryDslRunRepository();
    Instant t = Instant.parse("2026-08-13T10:00:00Z");
    repo.save(run("run-1", "OrderProcess", DslRunStatus.COMPLETED.name(), t, "RUN"));
    repo.save(run("run-2", "InvoiceProcess", DslRunStatus.COMPLETED.name(), t, "RUN"));

    DslRunSearchResult result = repo.search("OrderProcess", null, null, null, 0, 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items()).extracting(DslRun::runId).containsExactly("run-1");
  }

  @Test
  void searchFiltersByStatusCaseInsensitively() {
    var repo = new InMemoryDslRunRepository();
    Instant t = Instant.parse("2026-08-13T10:00:00Z");
    repo.save(run("run-1", "OrderProcess", DslRunStatus.COMPLETED.name(), t, "RUN"));
    repo.save(run("run-2", "OrderProcess", DslRunStatus.RUNNING.name(), t, "RUN"));

    DslRunSearchResult result = repo.search(null, "completed", null, null, 0, 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().get(0).runId()).isEqualTo("run-1");
  }

  @Test
  void searchFiltersByModeCaseInsensitivelyAndDefaultsNullToRun() {
    var repo = new InMemoryDslRunRepository();
    Instant t = Instant.parse("2026-08-13T10:00:00Z");
    repo.save(run("run-1", "OrderProcess", DslRunStatus.COMPLETED.name(), t, null));
    repo.save(run("run-2", "OrderProcess", DslRunStatus.COMPLETED.name(), t, "PREVIEW"));

    DslRunSearchResult result = repo.search(null, null, "run", null, 0, 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().get(0).runId()).isEqualTo("run-1");
  }

  @Test
  void searchCombinesFiltersAndReportsTotalIndependentOfLimit() {
    var repo = new InMemoryDslRunRepository();
    Instant t = Instant.parse("2026-08-13T10:00:00Z");
    repo.save(run("run-1", "OrderProcess", DslRunStatus.COMPLETED.name(), t, "RUN"));
    repo.save(run("run-2", "OrderProcess", DslRunStatus.COMPLETED.name(), t, "RUN"));
    repo.save(run("run-3", "OrderProcess", DslRunStatus.FAILED.name(), t, "RUN"));

    DslRunSearchResult result = repo.search("OrderProcess", "COMPLETED", null, null, 0, 1);

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.items()).hasSize(1);
  }

  @Test
  void searchAppliesOffsetAndLimit() {
    var repo = new InMemoryDslRunRepository();
    Instant t1 = Instant.parse("2026-08-13T10:00:00Z");
    Instant t2 = Instant.parse("2026-08-13T10:01:00Z");
    Instant t3 = Instant.parse("2026-08-13T10:02:00Z");
    repo.save(run("run-1", "OrderProcess", DslRunStatus.COMPLETED.name(), t1, "RUN"));
    repo.save(run("run-2", "OrderProcess", DslRunStatus.COMPLETED.name(), t2, "RUN"));
    repo.save(run("run-3", "OrderProcess", DslRunStatus.COMPLETED.name(), t3, "RUN"));

    DslRunSearchResult result = repo.search(null, null, null, null, 1, 1);

    assertThat(result.total()).isEqualTo(3);
    assertThat(result.items()).extracting(DslRun::runId).containsExactly("run-2");
  }

  @Test
  void searchOffsetBeyondTotalReturnsEmptyItemsWithTotal() {
    var repo = new InMemoryDslRunRepository();
    Instant t = Instant.parse("2026-08-13T10:00:00Z");
    repo.save(run("run-1", "OrderProcess", DslRunStatus.COMPLETED.name(), t, "RUN"));

    DslRunSearchResult result = repo.search(null, null, null, null, 10, 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items()).isEmpty();
  }

}
