package cbs.nova.starter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.IntegrationTestApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Testcontainers
@SpringBootTest(classes = IntegrationTestApplication.class, properties = {
    "dsl.worker.enabled=false"})
class DslRunRetentionIntegrationTest {

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

  @Autowired
  private DataSource dataSource;

  @BeforeAll
  static void applyMigrations() throws SQLException {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V1__create_dsl_runs.sql"),
            new ClassPathResource("db/migration/V2__add_context_json.sql"),
            new ClassPathResource("db/migration/V3__create_dsl_run_transactions.sql"),
            new ClassPathResource("db/migration/V4__dsl_runs_indexes.sql"));
    populator.setContinueOnError(false);
    try (Connection connection = DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      populator.populate(connection);
    }
  }

  @BeforeEach
  void cleanTable() {
    new JdbcTemplate(dataSource).execute("TRUNCATE TABLE dsl_runs");
  }

  @Test
  void purgeRemovesOldTerminalRunsAndKeepsRunningAndYoungRuns() {
    Instant now = Instant.parse("2026-06-15T12:00:00Z");
    Instant old = now.minusSeconds(7_200);
    Instant young = now.minusSeconds(1_800);
    Instant started = now.minusSeconds(300);

    repository.save(run("run-running", DslRunStatus.RUNNING, started, null));
    repository.save(run("run-young", DslRunStatus.COMPLETED, started, young));
    for (DslRunStatus status : List.of(DslRunStatus.COMPLETED, DslRunStatus.FAILED,
            DslRunStatus.STALE, DslRunStatus.CANCELLED)) {
      repository.save(run("run-" + status.name(), status, started, old));
    }

    int deleted = repository.purgeFinishedBefore(now.minusSeconds(3_600), 2);

    assertThat(deleted).isEqualTo(4);
    assertThat(repository.findByRunId("run-running")).isPresent();
    assertThat(repository.findByRunId("run-young")).isPresent();
    for (DslRunStatus status : List.of(DslRunStatus.COMPLETED, DslRunStatus.FAILED,
            DslRunStatus.STALE, DslRunStatus.CANCELLED)) {
      assertThat(repository.findByRunId("run-" + status.name())).isEmpty();
    }
  }

  private static DslRun run(String runId, DslRunStatus status, Instant startedAt,
          Instant finishedAt) {
    return DslRun.builder()
            .runId(runId)
            .processName("retention-test")
            .status(status.name())
            .input("{}")
            .output(null)
            .error(null)
            .startedAt(startedAt)
            .finishedAt(finishedAt)
            .executionMode(ExecutionMode.RUN.name())
            .build();
  }
}
