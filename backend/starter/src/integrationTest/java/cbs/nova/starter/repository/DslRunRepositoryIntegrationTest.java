package cbs.nova.starter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DslRun;
import cbs.nova.dsl.DslRunRepository;
import cbs.nova.dsl.DslRunStatus;
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
    DslRun run = new DslRun(
            runId,
            "SampleProcess",
            DslRunStatus.RUNNING.name(),
            "{\"foo\":\"bar\"}",
            null,
            null,
            Instant.now(),
            null,
            ExecutionMode.RUN.name());

    repository.save(run);

    Optional<DslRun> found = repository.findByRunId(runId);
    assertThat(found).isPresent();
    assertThat(found.get().processName()).isEqualTo("SampleProcess");
    assertThat(found.get().status()).isEqualTo(DslRunStatus.RUNNING.name());
  }

  @Test
  void updatesExistingRunOnSave() {
    String runId = "run-" + UUID.randomUUID();
    DslRun started = new DslRun(
            runId,
            "BatchProcessing",
            DslRunStatus.RUNNING.name(),
            "{\"items\":[]}",
            null,
            null,
            Instant.now(),
            null,
            ExecutionMode.RUN.name());
    repository.save(started);

    DslRun finished = new DslRun(
            runId,
            "BatchProcessing",
            DslRunStatus.COMPLETED.name(),
            started.input(),
            "{\"total\":6}",
            null,
            started.startedAt(),
            Instant.now(),
            ExecutionMode.RUN.name());
    repository.save(finished);

    Optional<DslRun> found = repository.findByRunId(runId);
    assertThat(found).isPresent();
    assertThat(found.get().status()).isEqualTo(DslRunStatus.COMPLETED.name());
    assertThat(found.get().output()).isEqualTo("{\"total\":6}");
    assertThat(repository.findByProcessName("BatchProcessing")).hasSize(1);
  }
}
