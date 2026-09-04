package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunSearchResult;
import cbs.nova.dsl.history.DslRunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Sql(scripts = {"classpath:db/migration/h2/V1__init.sql", "classpath:sql/truncate-dsl-tables.sql"})
@TestPropertySource(properties = {
    "csb.dsl.worker.enabled=false"
})
class JdbcDslRunRepositoryTest {

  @Autowired
  private DslRunRepository repository;

  @Test
  void saveAndFindByRunIdRoundTrip() {
    DslRun run = run("run-1", DslRunStatus.RUNNING, null, null);

    repository.save(run);
    Optional<DslRun> found = repository.findByRunId("run-1");

    assertThat(found).isPresent();
    assertThat(found.get().runId()).isEqualTo("run-1");
    assertThat(found.get().processName()).isEqualTo("process-1");
    assertThat(found.get().status()).isEqualTo(DslRunStatus.RUNNING.name());
    assertThat(found.get().input()).isEqualTo("{\"input\":true}");
  }

  @Test
  void findByRunIdReturnsEmptyWhenMissing() {
    assertThat(repository.findByRunId("missing")).isEmpty();
  }

  @Test
  void findByProcessNameReturnsOnlyMatchingRuns() {
    repository.save(run("run-a", DslRunStatus.RUNNING, null, null, "proc-a"));
    repository.save(run("run-b", DslRunStatus.RUNNING, null, null, "proc-b"));
    repository.save(run("run-c", DslRunStatus.RUNNING, null, null, "proc-a"));

    List<DslRun> found = repository.findByProcessName("proc-a");

    assertThat(found).hasSize(2);
    assertThat(found).extracting(DslRun::runId).containsExactlyInAnyOrder("run-a", "run-c");
  }

  @Test
  void updateFinishedMutatesOnlyFinalFields() {
    Instant startedAt = Instant.parse("2025-01-01T00:00:00Z");
    DslRun run = DslRun.builder()
            .runId("run-2")
            .processName("process-2")
            .status(DslRunStatus.RUNNING.name())
            .input("{\"input\":true}")
            .output(null)
            .error(null)
            .startedAt(startedAt)
            .finishedAt(null)
            .executionMode(ExecutionMode.RUN.name())
            .build();
    repository.save(run);

    Instant finishedAt = Instant.parse("2025-01-01T01:00:00Z");
    DslRun updated = repository.updateFinished(
            "run-2",
            DslRunStatus.COMPLETED.name(),
            "{\"output\":true}",
            null,
            finishedAt,
            "{\"trace\":[\"step\"]}");

    assertThat(updated.status()).isEqualTo(DslRunStatus.COMPLETED.name());
    assertThat(updated.output()).isEqualTo("{\"output\":true}");
    assertThat(updated.error()).isNull();
    assertThat(updated.finishedAt()).isEqualTo(finishedAt);

    DslRun persisted = repository.findByRunId("run-2").orElseThrow();
    assertThat(persisted.status()).isEqualTo(DslRunStatus.COMPLETED.name());
    assertThat(persisted.output()).isEqualTo("{\"output\":true}");
    assertThat(persisted.input()).isEqualTo("{\"input\":true}");
    assertThat(persisted.startedAt()).isEqualTo(startedAt);
    assertThat(persisted.contextJson()).isEqualTo("{\"trace\":[\"step\"]}");
  }

  @Test
  void updateFinishedThrowsWhenRunIdMissing() {
    assertThatThrownBy(() -> repository.updateFinished(
            "missing",
            DslRunStatus.FAILED.name(),
            null,
            "error",
            Instant.now(),
            null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Run not found: missing");
  }

  @Test
  void updateFinishedIfRunningIsNoOpWhenRunAlreadyTerminal() {
    Instant finishedAt = Instant.parse("2025-01-01T01:00:00Z");
    repository.save(run("run-stale-skip", DslRunStatus.COMPLETED, "{\"output\":true}", null));

    int affected = repository.updateFinishedIfRunning(
            "run-stale-skip",
            DslRunStatus.STALE.name(),
            "{}",
            "stale overwrite",
            finishedAt,
            null);

    assertThat(affected).isZero();
    DslRun persisted = repository.findByRunId("run-stale-skip").orElseThrow();
    assertThat(persisted.status()).isEqualTo(DslRunStatus.COMPLETED.name());
    assertThat(persisted.output()).isEqualTo("{\"output\":true}");
    assertThat(persisted.error()).isNull();
  }

  @Test
  void updateFinishedIfRunningTransitionsRunningRun() {
    Instant finishedAt = Instant.parse("2025-01-01T01:00:00Z");
    repository.save(run("run-stale-yes", DslRunStatus.RUNNING, null, null));

    int affected = repository.updateFinishedIfRunning(
            "run-stale-yes",
            DslRunStatus.STALE.name(),
            "{}",
            "stale",
            finishedAt,
            null);

    assertThat(affected).isEqualTo(1);
    DslRun persisted = repository.findByRunId("run-stale-yes").orElseThrow();
    assertThat(persisted.status()).isEqualTo(DslRunStatus.STALE.name());
    assertThat(persisted.output()).isEqualTo("{}");
  }

  @Test
  void saveAndFindByRunIdRoundTripsTriggeredBy() {
    DslRun run = DslRun.builder()
            .runId("run-triggered")
            .processName("process-1")
            .status(DslRunStatus.RUNNING.name())
            .input("{\"input\":true}")
            .output(null)
            .error(null)
            .startedAt(Instant.now())
            .finishedAt(null)
            .executionMode(ExecutionMode.RUN.name())
            .triggeredBy("alice@example.com")
            .build();

    repository.save(run);
    DslRun persisted = repository.findByRunId("run-triggered").orElseThrow();

    assertThat(persisted.triggeredBy()).isEqualTo("alice@example.com");
  }

  @Test
  void saveWithoutTriggeredByReadsBackNull() {
    DslRun run = DslRun.builder()
            .runId("run-no-trigger")
            .processName("process-1")
            .status(DslRunStatus.RUNNING.name())
            .input("{\"input\":true}")
            .output(null)
            .error(null)
            .startedAt(Instant.now())
            .finishedAt(null)
            .executionMode(ExecutionMode.RUN.name())
            .build();

    repository.save(run);
    DslRun persisted = repository.findByRunId("run-no-trigger").orElseThrow();

    assertThat(persisted.triggeredBy()).isNull();
  }

  @Test
  void saveAndFindByRunIdRoundTripsCorrelationId() {
    DslRun run = DslRun.builder()
            .runId("run-corr")
            .processName("process-1")
            .status(DslRunStatus.RUNNING.name())
            .input("{\"input\":true}")
            .output(null)
            .error(null)
            .startedAt(Instant.now())
            .finishedAt(null)
            .executionMode(ExecutionMode.RUN.name())
            .correlationId("corr-abc")
            .build();

    repository.save(run);
    DslRun persisted = repository.findByRunId("run-corr").orElseThrow();

    assertThat(persisted.correlationId()).isEqualTo("corr-abc");
  }

  @Test
  void saveWithoutCorrelationIdReadsBackNull() {
    DslRun run = DslRun.builder()
            .runId("run-no-corr")
            .processName("process-1")
            .status(DslRunStatus.RUNNING.name())
            .input("{\"input\":true}")
            .output(null)
            .error(null)
            .startedAt(Instant.now())
            .finishedAt(null)
            .executionMode(ExecutionMode.RUN.name())
            .build();

    repository.save(run);
    DslRun persisted = repository.findByRunId("run-no-corr").orElseThrow();

    assertThat(persisted.correlationId()).isNull();
  }

  @Test
  void searchFiltersByCorrelationId() {
    repository.save(run("run-c1", DslRunStatus.COMPLETED, null, null, "proc-c", "match-1"));
    repository.save(run("run-c2", DslRunStatus.COMPLETED, null, null, "proc-c", "other"));
    repository.save(run("run-c3", DslRunStatus.COMPLETED, null, null, "proc-c", null));

    DslRunSearchResult result = repository.search("proc-c", null, null, "match-1", 0, 10);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).runId()).isEqualTo("run-c1");
    assertThat(result.total()).isEqualTo(1);
  }

  @Test
  void searchWithoutCorrelationIdReturnsAll() {
    repository.save(run("run-c4", DslRunStatus.COMPLETED, null, null, "proc-d", "one"));
    repository.save(run("run-c5", DslRunStatus.COMPLETED, null, null, "proc-d", "two"));

    DslRunSearchResult result = repository.search("proc-d", null, null, null, 0, 10);

    assertThat(result.items()).hasSize(2);
  }

  @Test
  void searchResultsCarryTriggeredBy() {
    DslRun run = DslRun.builder()
            .runId("run-search")
            .processName("triggered-search-process")
            .status(DslRunStatus.COMPLETED.name())
            .input("{\"input\":true}")
            .output("{\"output\":true}")
            .error(null)
            .startedAt(Instant.now())
            .finishedAt(Instant.now())
            .executionMode(ExecutionMode.RUN.name())
            .triggeredBy("bob")
            .build();
    repository.save(run);

    DslRunSearchResult result = repository.search("triggered-search-process", null, null, null, 0,
            10);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).triggeredBy()).isEqualTo("bob");
  }
  private static DslRun run(String runId, DslRunStatus status, String output, String error) {
    return run(runId, status, output, error, "process-1", null);
  }

  private static DslRun run(
          String runId,
          DslRunStatus status,
          String output,
          String error,
          String processName) {
    return run(runId, status, output, error, processName, null);
  }

  private static DslRun run(
          String runId,
          DslRunStatus status,
          String output,
          String error,
          String processName,
          String correlationId) {
    return DslRun.builder()
            .runId(runId)
            .processName(processName)
            .status(status.name())
            .input("{\"input\":true}")
            .output(output)
            .error(error)
            .startedAt(Instant.now())
            .finishedAt(null)
            .executionMode(ExecutionMode.RUN.name())
            .correlationId(correlationId)
            .build();
  }

  @SpringBootApplication
  static class TestApplication {
    public static void main(String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }
}
