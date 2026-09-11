package cbs.nova.starter.persistence;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.entity.DslDefinitionTestEntity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC access to the {@code dsl_definition_tests} sidecar table (T409).
 *
 * <p>
 * Follows the {@link DslAuditRepository} idioms: constructor injection, named parameters, and an
 * explicit {@link RowMapper}. Reads are keyed by {@code definition_name}; authoring replaces a
 * whole definition's case set in one transaction ({@link #replaceAll}).
 */
@RequiredArgsConstructor
public class DslDefinitionTestRepository {

  private static final String COLUMNS = StarterConstants.DSL_DEFINITION_TEST_COLUMNS;

  private static final RowMapper<DslDefinitionTestEntity> ROW_MAPPER = (rs,
          rowNum) -> new DslDefinitionTestEntity(
                  rs.getLong("id"),
                  rs.getString("definition_name"),
                  rs.getString("case_name"),
                  rs.getString("input"),
                  rs.getString("expected_output"),
                  rs.getTimestamp("created_at").toInstant(),
                  rs.getTimestamp("updated_at").toInstant());

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;

  /** Lists all cases for a definition, ordered by case name for a stable report. */
  public List<DslDefinitionTestEntity> listForDefinition(String definitionName) {
    return jdbcTemplate.query("""
            SELECT %s FROM dsl_definition_tests
            WHERE definition_name = :definitionName
            ORDER BY case_name ASC
            """.formatted(COLUMNS),
            new MapSqlParameterSource("definitionName", definitionName), ROW_MAPPER);
  }

  /** Counts stored cases for a definition (used by tests/audit). */
  public int countByDefinition(String definitionName) {
    Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dsl_definition_tests WHERE definition_name = :definitionName",
            new MapSqlParameterSource("definitionName", definitionName), Long.class);
    return count != null ? count.intValue() : 0;
  }

  /** Appends one test case row. The database generates {@code id}. */
  public void insert(DslDefinitionTestEntity row) {
    jdbcTemplate.update("""
            INSERT INTO dsl_definition_tests
                    (definition_name, case_name, input, expected_output, created_at, updated_at)
            VALUES (:definitionName, :caseName, :input, :expectedOutput, :createdAt, :updatedAt)
            """, insertParams(row));
  }

  /**
   * Atomically replaces the whole case set for a definition: deletes existing rows for the
   * definition and inserts the given cases in a single transaction. A definition's cases are
   * authored as a unit, so a partial write must never be observable.
   */
  public void replaceAll(String definitionName, List<DslDefinitionTestEntity> rows) {
    Objects.requireNonNull(rows, "rows");
    transactionTemplate.executeWithoutResult(status -> {
      deleteForDefinition(definitionName);
      var now = Instant.now();
      for (DslDefinitionTestEntity row : rows) {
        insert(row.id() == null
                ? new DslDefinitionTestEntity(null, definitionName, row.caseName(),
                        row.inputJson(), row.expectedOutputJson(), now, now)
                : row.withUpdatedAt(now));
      }
    });
  }

  /** Deletes every case for a definition. */
  public void deleteForDefinition(String definitionName) {
    jdbcTemplate.update("DELETE FROM dsl_definition_tests WHERE definition_name = :definitionName",
            new MapSqlParameterSource("definitionName", definitionName));
  }

  private static MapSqlParameterSource insertParams(DslDefinitionTestEntity row) {
    return new MapSqlParameterSource()
            .addValue("definitionName", row.definitionName())
            .addValue("caseName", row.caseName())
            .addValue("input", row.inputJson())
            .addValue("expectedOutput", row.expectedOutputJson())
            .addValue("createdAt", Timestamp.from(row.createdAt()))
            .addValue("updatedAt", Timestamp.from(row.updatedAt()));
  }
}
