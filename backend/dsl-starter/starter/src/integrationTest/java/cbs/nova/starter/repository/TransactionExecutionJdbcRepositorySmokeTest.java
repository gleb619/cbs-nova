package cbs.nova.starter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.IntegrationTestApplication;
import cbs.nova.starter.entity.TransactionExecutionEntity;
import cbs.nova.starter.persistence.TransactionExecutionJdbcRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    "csb.dsl.worker.enabled=false"})
class TransactionExecutionJdbcRepositorySmokeTest {

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
  private TransactionExecutionJdbcRepository repository;

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
    TransactionExecutionEntity saved = repository.save(entity("run-tx-1", "txA", "RUNNING"));

    assertThat(saved.getId()).isNotNull();
    Optional<TransactionExecutionEntity> found = repository.findById(saved.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getTransactionName()).isEqualTo("txA");
  }

  @Test
  void saveWithExistingIdUpdatesRow() {
    TransactionExecutionEntity saved = repository.save(entity("run-tx-2", "txA", "RUNNING"));

    saved.setStatus("COMPLETED");
    repository.save(saved);

    assertThat(repository.count()).isEqualTo(1);
    assertThat(repository.findById(saved.getId()).orElseThrow().getStatus())
            .isEqualTo("COMPLETED");
  }

  @Test
  void saveAllFindAllCountAndExistsByIdWork() {
    repository.saveAll(List.of(entity("run-tx-3", "txA", "RUNNING"),
            entity("run-tx-3", "txB", "FAILED")));
    TransactionExecutionEntity third = repository.save(entity("run-tx-4", "txA", "RUNNING"));

    assertThat(repository.count()).isEqualTo(3);
    assertThat(repository.existsById(third.getId())).isTrue();
    assertThat(repository.existsById(third.getId() + 1000)).isFalse();
    assertThat(repository.findAll()).hasSize(3);
    assertThat(repository.findAllById(List.of(third.getId())))
            .extracting(TransactionExecutionEntity::getRunId)
            .containsExactly("run-tx-4");
  }

  @Test
  void deleteVariantsRemoveRows() {
    TransactionExecutionEntity first = repository.save(entity("run-tx-5", "txA", "RUNNING"));
    TransactionExecutionEntity second = repository.save(entity("run-tx-5", "txB", "RUNNING"));
    TransactionExecutionEntity third = repository.save(entity("run-tx-5", "txC", "RUNNING"));

    repository.deleteById(first.getId());
    assertThat(repository.count()).isEqualTo(2);

    repository.delete(second);
    assertThat(repository.count()).isEqualTo(1);

    repository.deleteAll(List.of(third));
    assertThat(repository.count()).isZero();

    TransactionExecutionEntity fourth = repository.save(entity("run-tx-6", "txA", "RUNNING"));
    TransactionExecutionEntity fifth = repository.save(entity("run-tx-6", "txB", "RUNNING"));
    repository.deleteAllById(List.of(fourth.getId(), fifth.getId()));
    assertThat(repository.count()).isZero();

    repository.saveAll(List.of(entity("run-tx-7", "txA", "RUNNING"),
            entity("run-tx-7", "txB", "RUNNING")));
    repository.deleteAll();
    assertThat(repository.count()).isZero();
  }

  @Test
  void findByRunIdOrderByIdDescReturnsNewestFirst() {
    repository.save(entity("run-tx-8", "txA", "RUNNING"));
    repository.save(entity("run-tx-8", "txB", "FAILED"));
    repository.save(entity("run-tx-8", "txC", "COMPLETED"));

    List<TransactionExecutionEntity> found = repository.findByRunIdOrderByIdDesc("run-tx-8");

    assertThat(found).extracting(TransactionExecutionEntity::getTransactionName)
            .containsExactly("txC", "txB", "txA");
  }

  @Test
  void findTopByRunIdAndTransactionNameReturnsLatestMatching() {
    repository.save(entity("run-tx-9", "txA", "FAILED"));
    TransactionExecutionEntity latest = repository.save(entity("run-tx-9", "txA", "COMPLETED"));
    repository.save(entity("run-tx-9", "txB", "RUNNING"));

    Optional<TransactionExecutionEntity> found = repository
            .findTopByRunIdAndTransactionNameOrderByIdDesc("run-tx-9", "txA");

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(latest.getId());
    assertThat(found.get().getStatus()).isEqualTo("COMPLETED");
    assertThat(repository.findTopByRunIdAndTransactionNameOrderByIdDesc("run-tx-9", "missing"))
            .isEmpty();
  }

  @Test
  void deleteByRunIdRemovesOnlyThatRun() {
    repository.save(entity("run-tx-10", "txA", "RUNNING"));
    repository.save(entity("run-tx-10", "txB", "RUNNING"));
    repository.save(entity("run-tx-11", "txA", "RUNNING"));

    repository.deleteByRunId("run-tx-10");

    assertThat(repository.count()).isEqualTo(1);
    assertThat(repository.findByRunIdOrderByIdDesc("run-tx-11")).hasSize(1);
  }

  @Test
  void deleteByRunIdsRemovesAllMatchingRuns() {
    repository.save(entity("run-tx-12", "txA", "RUNNING"));
    repository.save(entity("run-tx-13", "txA", "RUNNING"));
    repository.save(entity("run-tx-14", "txA", "RUNNING"));

    int affected = repository.deleteByRunIds(List.of("run-tx-12", "run-tx-13"));

    assertThat(affected).isEqualTo(2);
    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  void updateStatusAndErrorByRunIdAndTransactionNameUpdatesMatchingRowOnly() {
    repository.save(entity("run-tx-15", "txA", "RUNNING"));
    repository.save(entity("run-tx-15", "txB", "RUNNING"));

    int affected = repository.updateStatusAndErrorByRunIdAndTransactionName(
            "run-tx-15", "txA", "FAILED", "boom");

    assertThat(affected).isEqualTo(1);
    TransactionExecutionEntity updated = repository
            .findTopByRunIdAndTransactionNameOrderByIdDesc("run-tx-15", "txA")
            .orElseThrow();
    assertThat(updated.getStatus()).isEqualTo("FAILED");
    assertThat(updated.getErrorMessage()).isEqualTo("boom");
    TransactionExecutionEntity untouched = repository
            .findTopByRunIdAndTransactionNameOrderByIdDesc("run-tx-15", "txB")
            .orElseThrow();
    assertThat(untouched.getStatus()).isEqualTo("RUNNING");
    assertThat(untouched.getErrorMessage()).isNull();
    assertThat(repository.updateStatusAndErrorByRunIdAndTransactionName(
            "missing", "txA", "FAILED", "boom")).isZero();
  }

  private static TransactionExecutionEntity entity(String runId, String transactionName,
          String status) {
    TransactionExecutionEntity entity = new TransactionExecutionEntity();
    entity.setRunId(runId);
    entity.setTransactionName(transactionName);
    entity.setInputJson("{\"input\":true}");
    entity.setStatus(status);
    entity.setStartedAt(Instant.now());
    entity.setExecutedAt(Instant.now());
    return entity;
  }
}
