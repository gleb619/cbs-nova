package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.Driver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Testcontainers
class DslRunsMigrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

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
            new ClassPathResource("db/migration/postgres/V1__init.sql"));
    populator.setContinueOnError(false);

    SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
    dataSource.setDriverClass(Driver.class);
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

  @Test
  void migrationAddsNullableCorrelationIdColumn() throws Exception {
    try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();
      try (ResultSet columns = metaData.getColumns(null, null, "dsl_runs", "correlation_id")) {
        assertThat(columns.next()).isTrue();
        assertThat(columns.getString("TYPE_NAME")).isEqualToIgnoringCase("varchar");
        assertThat(columns.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNullable);
      }
    }
  }

  @Test
  void insertedRunRoundTripsCorrelationId() {
    String runId = "run-" + UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");

    jdbcTemplate.update(
            """
                    INSERT INTO dsl_runs (run_id, process_name, status, input_json, output_json,
                        error_message, context_json, started_at, finished_at, execution_mode, triggered_by, correlation_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            runId, "Loan", "RUNNING", "{}", null, null, null, Timestamp.from(startedAt), null,
            "RUN",
            "alice@example.com",
            "corr-123");

    String correlationId = jdbcTemplate.queryForObject(
            "SELECT correlation_id FROM dsl_runs WHERE run_id = ?", String.class, runId);

    assertThat(correlationId).isEqualTo("corr-123");
  }

  @Test
  void oldStyleInsertWithoutCorrelationIdReadsBackNull() {
    String runId = "run-" + UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");

    jdbcTemplate.update("""
            INSERT INTO dsl_runs (run_id, process_name, status, input_json, output_json,
                error_message, context_json, started_at, finished_at, execution_mode, triggered_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            runId, "Loan", "RUNNING", "{}", null, null, null, Timestamp.from(startedAt), null,
            "RUN",
            "alice@example.com");

    String correlationId = jdbcTemplate.queryForObject(
            "SELECT correlation_id FROM dsl_runs WHERE run_id = ?", String.class, runId);

    assertThat(correlationId).isNull();
  }

  @Test
  void correlationIdIndexExists() throws Exception {
    try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();
      try (ResultSet indexes = metaData.getIndexInfo(null, null, "dsl_runs", false, false)) {
        boolean found = false;
        while (indexes.next()) {
          if ("idx_dsl_runs_correlation_id".equals(indexes.getString("INDEX_NAME"))) {
            found = true;
            break;
          }
        }
        assertThat(found).isTrue();
      }
    }
  }

  @Test
  void finishedAtPartialIndexExistsAndIsUsedByPurgeQuery() {
    String indexDef = jdbcTemplate.queryForObject(
            """
                    SELECT indexdef FROM pg_indexes
                    WHERE tablename = 'dsl_runs' AND indexname = 'idx_dsl_runs_finished_at'""",
            String.class);

    assertThat(indexDef)
            .isNotNull()
            .contains("finished_at")
            .containsIgnoringCase("WHERE")
            .containsIgnoringCase("finished_at IS NOT NULL");

    seedFinishedAtIndexTestData();

    jdbcTemplate.execute("ANALYZE dsl_runs");

    Timestamp cutoff = Timestamp.from(Instant.parse("2020-02-15T00:00:00Z"));
    List<String> explainLines = jdbcTemplate.queryForList(
            """
                    EXPLAIN SELECT run_id FROM dsl_runs
                    WHERE finished_at < ? AND status <> 'RUNNING' LIMIT 500""",
            String.class,
            cutoff);

    String plan = String.join("\n", explainLines);

    // The partial index should be preferred when the predicate is selective.
    // If the test table is small enough that Postgres chooses a seq scan,
    // the metadata assertion above still proves the index exists; in that
    // case only assertion (1) is verified and the plan is logged.
    if (!plan.contains("idx_dsl_runs_finished_at")) {
      System.out.println("Postgres chose a plan without idx_dsl_runs_finished_at:\n" + plan);
    }
    assertThat(plan).contains("idx_dsl_runs_finished_at");
  }

  private void seedFinishedAtIndexTestData() {
    Instant base = Instant.parse("2020-01-01T00:00:00Z");
    long sixYearsInSeconds = 6L * 365 * 24 * 60 * 60;

    String sql = """
            INSERT INTO dsl_runs (run_id, process_name, status, input_json, output_json,
                error_message, context_json, started_at, finished_at, execution_mode, triggered_by, correlation_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    List<Object[]> terminalBatch = new ArrayList<>();
    for (int i = 0; i < 1950; i++) {
      Instant startedAt = base.plusSeconds((long) (Math.random() * sixYearsInSeconds));
      Instant finishedAt = startedAt.plusSeconds((long) (Math.random() * 300));
      terminalBatch.add(new Object[]{
          "run-" + UUID.randomUUID(),
          "PurgeIndexProbe",
          i % 3 == 0 ? "COMPLETED" : "FAILED",
          "{}",
          null,
          null,
          null,
          Timestamp.from(startedAt),
          Timestamp.from(finishedAt),
          "RUN",
          "test@example.com",
          "corr-" + i});
    }
    jdbcTemplate.batchUpdate(sql, terminalBatch);

    List<Object[]> runningBatch = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      Instant startedAt = base.plusSeconds((long) (Math.random() * sixYearsInSeconds));
      runningBatch.add(new Object[]{
          "run-" + UUID.randomUUID(),
          "PurgeIndexProbe",
          "RUNNING",
          "{}",
          null,
          null,
          null,
          Timestamp.from(startedAt),
          null,
          "RUN",
          "test@example.com",
          "running-corr-" + i});
    }
    jdbcTemplate.batchUpdate(sql, runningBatch);
  }

}
