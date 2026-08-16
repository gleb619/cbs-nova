package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "dsl.worker.enabled=false",
    "spring.flyway.enabled=true"
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

  private static DslRun run(String runId, DslRunStatus status, String output, String error) {
    return run(runId, status, output, error, "process-1");
  }

  private static DslRun run(
          String runId,
          DslRunStatus status,
          String output,
          String error,
          String processName) {
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
            .build();
  }

  @SpringBootApplication
  static class TestApplication {
    public static void main(String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }
}
