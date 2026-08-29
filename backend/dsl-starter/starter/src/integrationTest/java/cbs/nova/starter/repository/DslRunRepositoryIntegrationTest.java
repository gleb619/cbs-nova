package cbs.nova.starter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunSearchResult;
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

  @Test
  void searchWithoutFiltersReturnsAllRunsOrderedByStartedAtDesc() {
    Instant t1 = Instant.parse("2026-08-13T10:00:00Z");
    Instant t2 = Instant.parse("2026-08-13T10:01:00Z");
    Instant t3 = Instant.parse("2026-08-13T10:02:00Z");
    repository.save(run("run-1", "LoanDisbursement", DslRunStatus.COMPLETED.name(), t1,
            ExecutionMode.RUN.name()));
    repository.save(run("run-2", "CreditScoring", DslRunStatus.FAILED.name(), t2,
            ExecutionMode.RUN.name()));
    repository.save(run("run-3", "LoanDisbursement", DslRunStatus.RUNNING.name(), t3,
            ExecutionMode.PREVIEW.name()));

    DslRunSearchResult result = repository.search(null, null, null, 0, 10);

    assertThat(result.total()).isEqualTo(3);
    assertThat(result.items()).extracting(DslRun::runId).containsExactly("run-3", "run-2", "run-1");
  }

  @Test
  void searchFiltersByProcessName() {
    Instant t = Instant.parse("2026-08-13T10:00:00Z");
    repository.save(run("run-1", "LoanDisbursement", DslRunStatus.COMPLETED.name(), t,
            ExecutionMode.RUN.name()));
    repository.save(run("run-2", "CreditScoring", DslRunStatus.COMPLETED.name(), t,
            ExecutionMode.RUN.name()));

    DslRunSearchResult result = repository.search("CreditScoring", null, null, 0, 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().get(0).runId()).isEqualTo("run-2");
  }

  @Test
  void searchFiltersByStatusCaseInsensitively() {
    Instant t = Instant.parse("2026-08-13T10:00:00Z");
    repository.save(run("run-1", "LoanDisbursement", DslRunStatus.COMPLETED.name(), t,
            ExecutionMode.RUN.name()));
    repository.save(run("run-2", "LoanDisbursement", DslRunStatus.RUNNING.name(), t,
            ExecutionMode.RUN.name()));

    DslRunSearchResult result = repository.search(null, "completed", null, 0, 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().get(0).runId()).isEqualTo("run-1");
  }

  @Test
  void searchFiltersByModeAndDefaultsNullModeToRun() {
    Instant t = Instant.parse("2026-08-13T10:00:00Z");
    repository.save(run("run-1", "LoanDisbursement", DslRunStatus.COMPLETED.name(), t, null));
    repository.save(run("run-2", "LoanDisbursement", DslRunStatus.COMPLETED.name(), t,
            ExecutionMode.PREVIEW.name()));

    DslRunSearchResult result = repository.search(null, null, "run", 0, 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().get(0).runId()).isEqualTo("run-1");
  }

  @Test
  void searchCombinesFilters() {
    Instant t = Instant.parse("2026-08-13T10:00:00Z");
    repository.save(run("run-1", "LoanDisbursement", DslRunStatus.COMPLETED.name(), t,
            ExecutionMode.RUN.name()));
    repository.save(run("run-2", "LoanDisbursement", DslRunStatus.COMPLETED.name(), t,
            ExecutionMode.PREVIEW.name()));
    repository.save(run("run-3", "CreditScoring", DslRunStatus.COMPLETED.name(), t,
            ExecutionMode.RUN.name()));

    DslRunSearchResult result = repository.search("LoanDisbursement", "COMPLETED", "RUN", 0, 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items().get(0).runId()).isEqualTo("run-1");
  }

  @Test
  void searchLimitCapsItemsButNotTotal() {
    Instant t = Instant.parse("2026-08-13T10:00:00Z");
    for (int i = 1; i <= 5; i++) {
      repository.save(run("run-" + i, "LoanDisbursement", DslRunStatus.COMPLETED.name(), t,
              ExecutionMode.RUN.name()));
    }

    DslRunSearchResult result = repository.search("LoanDisbursement", null, null, 0, 2);

    assertThat(result.total()).isEqualTo(5);
    assertThat(result.items()).hasSize(2);
  }

  @Test
  void searchOffsetAndLimitReturnCorrectPage() {
    Instant base = Instant.parse("2026-08-13T10:00:00Z");
    for (int i = 1; i <= 5; i++) {
      repository.save(run("run-" + i, "LoanDisbursement", DslRunStatus.COMPLETED.name(),
              base.plusSeconds(i), ExecutionMode.RUN.name()));
    }

    DslRunSearchResult result = repository.search("LoanDisbursement", null, null, 2, 2);

    assertThat(result.total()).isEqualTo(5);
    assertThat(result.items()).extracting(DslRun::runId).containsExactly("run-3", "run-2");
  }

  @Test
  void searchOffsetBeyondTotalReturnsEmptyItemsWithTotal() {
    Instant t = Instant.parse("2026-08-13T10:00:00Z");
    repository.save(run("run-1", "LoanDisbursement", DslRunStatus.COMPLETED.name(), t,
            ExecutionMode.RUN.name()));

    DslRunSearchResult result = repository.search(null, null, null, 10, 10);

    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items()).isEmpty();
  }

  private DslRun run(String runId, String processName, String status, Instant startedAt,
          String mode) {
    return DslRun.builder()
            .runId(runId)
            .processName(processName)
            .status(status)
            .startedAt(startedAt)
            .finishedAt(
                    DslRunStatus.RUNNING.name().equals(status) ? null : startedAt.plusSeconds(5))
            .executionMode(mode)
            .build();
  }

}
