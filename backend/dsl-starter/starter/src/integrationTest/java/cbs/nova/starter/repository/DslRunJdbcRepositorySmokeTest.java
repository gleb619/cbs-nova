package cbs.nova.starter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.IntegrationTestApplication;
import cbs.nova.starter.entity.DslRunEntity;
import cbs.nova.starter.persistence.DslRunJdbcRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import javax.sql.DataSource;
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

@Testcontainers
@SpringBootTest(classes = IntegrationTestApplication.class, properties = {
    "cbs.dsl.worker.enabled=false"})
class DslRunJdbcRepositorySmokeTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired
  private DslRunJdbcRepository repository;

  @Autowired
  private DataSource dataSource;

  @BeforeAll
  static void applyMigrations() throws SQLException {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/postgres/V1__init.sql"));
    populator.setContinueOnError(false);
    try (Connection connection = DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      populator.populate(connection);
    }
  }

  @BeforeEach
  void cleanTables() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("TRUNCATE TABLE dsl_run_transactions");
    jdbcTemplate.execute("TRUNCATE TABLE dsl_runs");
  }

  @Test
  void saveInsertsNewEntity() {
    DslRunEntity saved = repository.save(entity("run-insert", "ProcessA", "RUNNING"));

    assertThat(saved.getId()).isNotNull();
    Optional<DslRunEntity> found = repository.findById(saved.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getRunId()).isEqualTo("run-insert");
  }

  @Test
  void saveWithExistingIdUpdatesRow() {
    DslRunEntity saved = repository.save(entity("run-update", "ProcessA", "RUNNING"));

    saved.setStatus("COMPLETED");
    saved.setOutputJson("{\"done\":true}");
    repository.save(saved);

    Optional<DslRunEntity> found = repository.findById(saved.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getStatus()).isEqualTo("COMPLETED");
    assertThat(found.get().getOutputJson()).isEqualTo("{\"done\":true}");
    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  void saveAllPersistsAllEntities() {
    List<DslRunEntity> saved = StreamSupport.stream(
            repository.saveAll(List.of(
                    entity("run-all-1", "ProcessA", "RUNNING"),
                    entity("run-all-2", "ProcessB", "FAILED"))).spliterator(),
            false).toList();

    assertThat(saved).allSatisfy(e -> assertThat(e.getId()).isNotNull());
    assertThat(repository.count()).isEqualTo(2);
  }

  @Test
  void existsByIdReflectsStoredRows() {
    DslRunEntity saved = repository.save(entity("run-exists", "ProcessA", "RUNNING"));

    assertThat(repository.existsById(saved.getId())).isTrue();
    assertThat(repository.existsById(saved.getId() + 1000)).isFalse();
  }

  @Test
  void findAllReturnsAllRows() {
    repository.saveAll(List.of(
            entity("run-all-a", "ProcessA", "RUNNING"),
            entity("run-all-b", "ProcessB", "COMPLETED")));

    assertThat(repository.findAll()).extracting(DslRunEntity::getRunId)
            .containsExactlyInAnyOrder("run-all-a", "run-all-b");
  }

  @Test
  void findAllByIdReturnsOnlyMatchingRows() {
    DslRunEntity first = repository.save(entity("run-byid-1", "ProcessA", "RUNNING"));
    DslRunEntity second = repository.save(entity("run-byid-2", "ProcessA", "RUNNING"));
    repository.save(entity("run-byid-3", "ProcessA", "RUNNING"));

    assertThat(repository.findAllById(List.of(first.getId(), second.getId())))
            .extracting(DslRunEntity::getRunId)
            .containsExactlyInAnyOrder("run-byid-1", "run-byid-2");
  }

  @Test
  void countReflectsStoredRows() {
    assertThat(repository.count()).isZero();
    repository.saveAll(List.of(entity("run-c-1", "ProcessA", "RUNNING"),
            entity("run-c-2", "ProcessA", "RUNNING")));
    assertThat(repository.count()).isEqualTo(2);
  }

  @Test
  void deleteByIdRemovesRow() {
    DslRunEntity saved = repository.save(entity("run-del-id", "ProcessA", "RUNNING"));

    repository.deleteById(saved.getId());

    assertThat(repository.existsById(saved.getId())).isFalse();
    assertThat(repository.count()).isZero();
  }

  @Test
  void deleteEntityRemovesRow() {
    DslRunEntity saved = repository.save(entity("run-del-entity", "ProcessA", "RUNNING"));

    repository.delete(saved);

    assertThat(repository.count()).isZero();
  }

  @Test
  void deleteAllWithEntitiesRemovesOnlyThoseRows() {
    DslRunEntity first = repository.save(entity("run-del-some-1", "ProcessA", "RUNNING"));
    DslRunEntity second = repository.save(entity("run-del-some-2", "ProcessA", "RUNNING"));
    repository.save(entity("run-del-some-3", "ProcessA", "RUNNING"));

    repository.deleteAll(List.of(first, second));

    assertThat(repository.findAll()).extracting(DslRunEntity::getRunId)
            .containsExactly("run-del-some-3");
  }

  @Test
  void deleteAllRemovesEveryRow() {
    repository.saveAll(List.of(entity("run-del-all-1", "ProcessA", "RUNNING"),
            entity("run-del-all-2", "ProcessA", "RUNNING")));

    repository.deleteAll();

    assertThat(repository.count()).isZero();
  }

  @Test
  void deleteAllByIdRemovesMatchingRows() {
    DslRunEntity first = repository.save(entity("run-delids-1", "ProcessA", "RUNNING"));
    DslRunEntity second = repository.save(entity("run-delids-2", "ProcessA", "RUNNING"));
    repository.save(entity("run-delids-3", "ProcessA", "RUNNING"));

    repository.deleteAllById(List.of(first.getId(), second.getId()));

    assertThat(repository.findAll()).extracting(DslRunEntity::getRunId)
            .containsExactly("run-delids-3");
  }

  @Test
  void findByRunIdReturnsMatchingRow() {
    repository.save(entity("run-lookup", "ProcessA", "RUNNING"));

    Optional<DslRunEntity> found = repository.findByRunId("run-lookup");

    assertThat(found).isPresent();
    assertThat(found.get().getProcessName()).isEqualTo("ProcessA");
    assertThat(repository.findByRunId("missing")).isEmpty();
  }

  @Test
  void findByProcessNameReturnsMatchingRows() {
    repository.saveAll(List.of(
            entity("run-proc-1", "ProcessA", "RUNNING"),
            entity("run-proc-2", "ProcessB", "RUNNING"),
            entity("run-proc-3", "ProcessA", "RUNNING")));

    assertThat(repository.findByProcessName("ProcessA"))
            .extracting(DslRunEntity::getRunId)
            .containsExactlyInAnyOrder("run-proc-1", "run-proc-3");
  }

  @Test
  void updateFinishedByRunIdUpdatesFinalFields() {
    repository.save(entity("run-finish", "ProcessA", "RUNNING"));
    Instant finishedAt = Instant.parse("2026-09-01T12:00:00Z");

    int affected = repository.updateFinishedByRunId(
            "run-finish", "COMPLETED", "{\"out\":1}", null, "{\"ctx\":2}", finishedAt);

    assertThat(affected).isEqualTo(1);
    DslRunEntity found = repository.findByRunId("run-finish").orElseThrow();
    assertThat(found.getStatus()).isEqualTo("COMPLETED");
    assertThat(found.getOutputJson()).isEqualTo("{\"out\":1}");
    assertThat(found.getContextJson()).isEqualTo("{\"ctx\":2}");
    assertThat(found.getFinishedAt()).isEqualTo(finishedAt);
    assertThat(found.getInputJson()).isEqualTo("{\"input\":true}");
  }

  @Test
  void updateFinishedByRunIdReturnsZeroWhenRunMissing() {
    int affected = repository.updateFinishedByRunId(
            "missing", "COMPLETED", "{}", null, null, Instant.now());

    assertThat(affected).isZero();
  }

  @Test
  void updateStatusByRunIdUpdatesOnlyStatus() {
    repository.save(entity("run-status", "ProcessA", "RUNNING"));

    int affected = repository.updateStatusByRunId("run-status", "STALE");

    assertThat(affected).isEqualTo(1);
    DslRunEntity found = repository.findByRunId("run-status").orElseThrow();
    assertThat(found.getStatus()).isEqualTo("STALE");
    assertThat(found.getOutputJson()).isNull();
    assertThat(repository.updateStatusByRunId("missing", "STALE")).isZero();
  }

  @Test
  void updateFinishedIfRunningReturnsZeroWhenStatusIsNotRunning() {
    repository.save(entity("run-terminal", "ProcessA", "COMPLETED"));

    int affected = repository.updateFinishedIfRunning(
            "run-terminal", "STALE", "{}", "overwrite", null, Instant.now());

    assertThat(affected).isZero();
    DslRunEntity found = repository.findByRunId("run-terminal").orElseThrow();
    assertThat(found.getStatus()).isEqualTo("COMPLETED");
    assertThat(found.getOutputJson()).isNull();
  }

  @Test
  void updateFinishedIfRunningUpdatesRunningRun() {
    repository.save(entity("run-active", "ProcessA", "RUNNING"));
    Instant finishedAt = Instant.parse("2026-09-01T12:00:00Z");

    int affected = repository.updateFinishedIfRunning(
            "run-active", "STALE", "{}", "stale", null, finishedAt);

    assertThat(affected).isEqualTo(1);
    DslRunEntity found = repository.findByRunId("run-active").orElseThrow();
    assertThat(found.getStatus()).isEqualTo("STALE");
    assertThat(found.getFinishedAt()).isEqualTo(finishedAt);
  }

  private static DslRunEntity entity(String runId, String processName, String status) {
    DslRunEntity entity = new DslRunEntity();
    entity.setRunId(runId);
    entity.setProcessName(processName);
    entity.setStatus(status);
    entity.setInputJson("{\"input\":true}");
    entity.setStartedAt(Instant.now());
    return entity;
  }
}
