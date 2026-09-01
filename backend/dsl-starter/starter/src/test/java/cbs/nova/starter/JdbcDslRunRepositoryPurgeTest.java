package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Sql(scripts = {"classpath:db/migration/h2/V1__init.sql", "classpath:sql/truncate-dsl-tables.sql"})
@TestPropertySource(properties = {
    "dsl.worker.enabled=false"
})
class JdbcDslRunRepositoryPurgeTest {

  @Autowired
  private DslRunRepository repository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanTable() {
    jdbcTemplate.execute("TRUNCATE TABLE dsl_runs");
    jdbcTemplate.execute("TRUNCATE TABLE dsl_run_transactions");
  }

  @Test
  void purgeFinishedBeforeDeletesOldTerminalRowsAcrossAllStatuses() {
    Instant now = Instant.now();
    Instant old = now.minusSeconds(3_600);
    for (DslRunStatus status : List.of(DslRunStatus.COMPLETED, DslRunStatus.FAILED,
            DslRunStatus.STALE, DslRunStatus.CANCELLED)) {
      repository.save(run("run-" + status.name(), status, now.minusSeconds(300), old));
    }

    int deleted = repository.purgeFinishedBefore(now, 100);

    assertThat(deleted).isEqualTo(4);
    for (DslRunStatus status : List.of(DslRunStatus.COMPLETED, DslRunStatus.FAILED,
            DslRunStatus.STALE, DslRunStatus.CANCELLED)) {
      assertThat(repository.findByRunId("run-" + status.name())).isEmpty();
    }
  }

  @Test
  void purgeFinishedBeforeNeverDeletesRunningRows() {
    Instant now = Instant.now();
    repository.save(run("run-live", DslRunStatus.RUNNING, now.minusSeconds(3_600), null));

    int deleted = repository.purgeFinishedBefore(now, 100);

    assertThat(deleted).isZero();
    assertThat(repository.findByRunId("run-live")).isPresent();
  }

  @Test
  void purgeFinishedBeforeKeepsYoungTerminalRows() {
    Instant now = Instant.now();
    Instant recent = now.plusSeconds(60);
    repository.save(run("run-recent", DslRunStatus.COMPLETED, now.minusSeconds(300), recent));

    int deleted = repository.purgeFinishedBefore(now, 100);

    assertThat(deleted).isZero();
    assertThat(repository.findByRunId("run-recent")).isPresent();
  }

  @Test
  void purgeFinishedBeforeUsesStrictCutoff() {
    Instant cutoff = Instant.parse("2025-02-01T12:00:00Z");
    repository.save(run("run-exact", DslRunStatus.COMPLETED, cutoff.minusSeconds(300), cutoff));
    repository.save(run("run-older", DslRunStatus.COMPLETED, cutoff.minusSeconds(300),
            cutoff.minusSeconds(1)));

    int deleted = repository.purgeFinishedBefore(cutoff, 100);

    assertThat(deleted).isEqualTo(1);
    assertThat(repository.findByRunId("run-exact")).isPresent();
    assertThat(repository.findByRunId("run-older")).isEmpty();
  }

  @Test
  void purgeFinishedBeforeLoopsUntilTableIsDrainedInBatches() {
    Instant now = Instant.now();
    for (int i = 0; i < 5; i++) {
      repository.save(run("run-old-" + i, DslRunStatus.COMPLETED, now.minusSeconds(600),
              now.minusSeconds(3_600)));
    }

    int deleted = repository.purgeFinishedBefore(now, 2);

    assertThat(deleted).isEqualTo(5);
    assertThat(repository.findByRunId("run-old-0")).isEmpty();
  }

  @Test
  void purgeFinishedBeforeRequiresPositiveBatchSize() {
    assertThatThrownBy(() -> repository.purgeFinishedBefore(Instant.now(), 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batchSize must be positive");
    assertThatThrownBy(() -> repository.purgeFinishedBefore(Instant.now(), -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batchSize must be positive");
  }

  @Test
  void purgeFinishedBeforeNotifiesConsumerBeforeParentDelete() {
    Instant now = Instant.now();
    for (int i = 0; i < 3; i++) {
      repository.save(run("run-batch-" + i, DslRunStatus.COMPLETED, now.minusSeconds(600),
              now.minusSeconds(3_600)));
    }

    List<List<String>> captured = new ArrayList<>();
    int deleted = repository.purgeFinishedBefore(now, 2, ids -> {
      captured.add(new ArrayList<>(ids));
      for (String runId : ids) {
        assertThat(repository.findByRunId(runId)).isPresent();
      }
    });

    assertThat(deleted).isEqualTo(3);
    assertThat(captured).hasSize(2);
    assertThat(captured.get(0)).hasSize(2);
    assertThat(captured.get(1)).hasSize(1);
    for (String runId : List.of("run-batch-0", "run-batch-1", "run-batch-2")) {
      assertThat(repository.findByRunId(runId)).isEmpty();
    }
  }

  private static DslRun run(String runId, DslRunStatus status, Instant startedAt,
          Instant finishedAt) {
    return DslRun.builder()
            .runId(runId)
            .processName("process-1")
            .status(status.name())
            .input("{\"input\":true}")
            .output(null)
            .error(null)
            .startedAt(startedAt)
            .finishedAt(finishedAt)
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
