package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Testcontainers
class DslRunsMigrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  private static JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void applyMigrations() throws Exception {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V1__create_dsl_runs.sql"),
            new ClassPathResource("db/migration/V2__add_context_json.sql"),
            new ClassPathResource("db/migration/V3__create_dsl_run_transactions.sql"),
            new ClassPathResource("db/migration/V4__dsl_runs_indexes.sql"),
            new ClassPathResource("db/migration/V5__dsl_runs_triggered_by.sql"));
    populator.setContinueOnError(false);

    SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
    dataSource.setDriverClass(org.postgresql.Driver.class);
    dataSource.setUrl(postgres.getJdbcUrl());
    dataSource.setUsername(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());
    jdbcTemplate = new JdbcTemplate(dataSource);

    try (Connection connection = dataSource.getConnection()) {
      populator.populate(connection);
    }
  }

  @Test
  void migrationAddsNullableTriggeredByColumn() throws Exception {
    try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();
      try (ResultSet columns = metaData.getColumns(null, null, "dsl_runs", "triggered_by")) {
        assertThat(columns.next()).isTrue();
        assertThat(columns.getString("TYPE_NAME")).isEqualToIgnoringCase("varchar");
        assertThat(columns.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNullable);
      }
    }
  }

  @Test
  void insertedRunRoundTripsTriggeredBy() {
    String runId = "run-" + UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");

    jdbcTemplate.update("""
            INSERT INTO dsl_runs (run_id, process_name, status, input_json, output_json,
                error_message, context_json, started_at, finished_at, execution_mode, triggered_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            runId, "Loan", "RUNNING", "{}", null, null, null, Timestamp.from(startedAt), null,
            "RUN",
            "alice@example.com");

    String triggeredBy = jdbcTemplate.queryForObject(
            "SELECT triggered_by FROM dsl_runs WHERE run_id = ?", String.class, runId);

    assertThat(triggeredBy).isEqualTo("alice@example.com");
  }

  @Test
  void oldStyleInsertWithoutTriggeredByReadsBackNull() {
    String runId = "run-" + UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");

    jdbcTemplate.update("""
            INSERT INTO dsl_runs (run_id, process_name, status, input_json, output_json,
                error_message, context_json, started_at, finished_at, execution_mode)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            runId, "Loan", "RUNNING", "{}", null, null, null, Timestamp.from(startedAt), null,
            "RUN");

    String triggeredBy = jdbcTemplate.queryForObject(
            "SELECT triggered_by FROM dsl_runs WHERE run_id = ?", String.class, runId);

    assertThat(triggeredBy).isNull();
  }
}
