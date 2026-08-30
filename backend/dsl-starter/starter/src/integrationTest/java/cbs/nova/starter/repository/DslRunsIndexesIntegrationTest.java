package cbs.nova.starter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.IntegrationTestApplication;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    "dsl.worker.enabled=false"})
class DslRunsIndexesIntegrationTest {

  private static final String TABLE = "dsl_runs";

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
  private DataSource dataSource;

  @BeforeAll
  static void applyMigrations() throws SQLException {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V1__create_dsl_runs.sql"),
            new ClassPathResource("db/migration/V2__add_context_json.sql"),
            new ClassPathResource("db/migration/V3__create_dsl_run_transactions.sql"),
            new ClassPathResource("db/migration/V4__dsl_runs_indexes.sql"),
            new ClassPathResource("db/migration/V5__dsl_runs_triggered_by.sql"));
    populator.setContinueOnError(false);
    try (Connection connection = DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      populator.populate(connection);
    }
  }

  @BeforeEach
  void cleanTable() {
    jdbc().execute("TRUNCATE TABLE " + TABLE);
  }

  @Test
  void flywayAppliedV4Indexes() throws SQLException {
    Set<String> indexNames = readIndexNames();
    assertThat(indexNames)
            .contains(
                    "idx_dsl_runs_status_started_at",
                    "idx_dsl_runs_process_started_at",
                    "idx_dsl_runs_execution_mode");
  }

  @Test
  void indexBackedStatusFilterReturnsMatchingRows() {
    Instant base = Instant.parse("2026-01-01T00:00:00Z");
    insert("orders", "RUNNING", "RUN", base);
    insert("orders", "COMPLETED", "RUN", base.plusSeconds(60));
    insert("orders", "RUNNING", "PREVIEW", base.plusSeconds(120));
    insert("shipping", "RUNNING", "RUN", base.plusSeconds(180));

    List<String> runningOrders = jdbc().queryForList(
            "SELECT run_id FROM dsl_runs WHERE status = ? AND process_name = ? ORDER BY started_at DESC",
            String.class, "RUNNING", "orders");

    assertThat(runningOrders).hasSize(2);
  }

  @Test
  void indexBackedProcessNameLookupReturnsAllRowsForProcess() {
    Instant base = Instant.parse("2026-01-02T00:00:00Z");
    for (int i = 0; i < 4; i++) {
      insert("billing", i % 2 == 0 ? "COMPLETED" : "FAILED",
              "RUN", base.plusSeconds(i * 30L));
    }
    insert("shipping", "RUNNING", "RUN", base.plusSeconds(500));

    List<String> billingRunIds = jdbc().queryForList(
            "SELECT run_id FROM dsl_runs WHERE process_name = ? ORDER BY started_at DESC",
            String.class, "billing");

    assertThat(billingRunIds).hasSize(4);
  }

  @Test
  void indexBackedExecutionModeFilterReturnsMatchingRows() {
    Instant base = Instant.parse("2026-01-03T00:00:00Z");
    insert("orders", "RUNNING", "PREVIEW", base);
    insert("orders", "COMPLETED", "PREVIEW", base.plusSeconds(30));
    insert("orders", "COMPLETED", "RUN", base.plusSeconds(60));
    insert("shipping", "RUNNING", "PREVIEW", base.plusSeconds(90));

    List<String> previewRunIds = jdbc().queryForList(
            "SELECT run_id FROM dsl_runs WHERE execution_mode = ? ORDER BY started_at DESC",
            String.class, "PREVIEW");

    assertThat(previewRunIds).hasSize(3);
  }

  @Test
  void migrationV4IsIdempotent() throws SQLException {
    long indexCountBefore = readIndexNames().size();
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V4__dsl_runs_indexes.sql"),
            new ClassPathResource("db/migration/V5__dsl_runs_triggered_by.sql"));
    populator.setContinueOnError(false);
    try (Connection connection = DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      populator.populate(connection);
    }
    long indexCountAfter = readIndexNames().size();
    assertThat(indexCountAfter).isEqualTo(indexCountBefore);
  }

  private void insert(String processName, String status, String mode, Instant startedAt) {
    jdbc().update(
            "INSERT INTO dsl_runs (run_id, process_name, status, input_json, started_at, execution_mode) "
                    + "VALUES (?, ?, ?, ?, ?, ?)",
            "run-" + java.util.UUID.randomUUID(),
            processName,
            status,
            "{}",
            Timestamp.from(startedAt),
            mode);
  }

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  private Set<String> readIndexNames() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData meta = connection.getMetaData();
      try (ResultSet rs = meta.getIndexInfo(null, null, TABLE, false, false)) {
        Set<String> names = new java.util.HashSet<>();
        while (rs.next()) {
          String name = rs.getString("INDEX_NAME");
          if (name != null) {
            names.add(name.toLowerCase(Locale.ROOT));
          }
        }
        return names;
      }
    }
  }
}
