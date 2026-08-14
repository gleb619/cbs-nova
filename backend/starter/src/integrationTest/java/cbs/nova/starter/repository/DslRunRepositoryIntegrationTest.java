package cbs.nova.starter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.starter.IntegrationTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Testcontainers
@SpringBootTest(classes = IntegrationTestApplication.class, properties = {
    "dsl.worker.enabled=false"})
class DslRunRepositoryIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired
  private DslRunRepository repository;

  @Test
  void savesRunAndFindsItByRunId() {
    String runId = "run-" + UUID.randomUUID();
    DslRun run = DslRun.builder()
            .runId(runId)
            .processName("SampleProcess")
            .status(DslRunStatus.RUNNING.name())
            .input("{\"foo\":\"bar\"}")
            .output(null)
            .error(null)
            .startedAt(Instant.now())
            .finishedAt(null)
            .executionMode(ExecutionMode.RUN.name())
            .build();

    repository.save(run);

    Optional<DslRun> found = repository.findByRunId(runId);
    assertThat(found).isPresent();
    assertThat(found.get().processName()).isEqualTo("SampleProcess");
    assertThat(found.get().status()).isEqualTo(DslRunStatus.RUNNING.name());
  }

  @Test
  void updatesExistingRunOnSave() {
    String runId = "run-" + UUID.randomUUID();
    DslRun started = DslRun.builder()
            .runId(runId)
            .processName("BatchProcessing")
            .status(DslRunStatus.RUNNING.name())
            .input("{\"items\":[]}")
            .output(null)
            .error(null)
            .startedAt(Instant.now())
            .finishedAt(null)
            .executionMode(ExecutionMode.RUN.name())
            .build();
    repository.save(started);

    DslRun finished = DslRun.builder()
            .runId(runId)
            .processName("BatchProcessing")
            .status(DslRunStatus.COMPLETED.name())
            .input(started.input())
            .output("{\"total\":6}")
            .error(null)
            .startedAt(started.startedAt())
            .finishedAt(Instant.now())
            .executionMode(ExecutionMode.RUN.name())
            .build();
    repository.save(finished);

    Optional<DslRun> found = repository.findByRunId(runId);
    assertThat(found).isPresent();
    assertThat(found.get().status()).isEqualTo(DslRunStatus.COMPLETED.name());
    assertThat(found.get().output()).isEqualTo("{\"total\":6}");
    assertThat(repository.findByProcessName("BatchProcessing")).hasSize(1);
  }
}
