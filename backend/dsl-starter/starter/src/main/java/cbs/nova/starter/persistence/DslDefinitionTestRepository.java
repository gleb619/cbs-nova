package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslDefinitionTestEntity;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC access to the {@code dsl_definition_tests} sidecar table (T409).
 *
 * <p>
 * Follows the {@link DslEventRepository} idioms: constructor injection, named parameters, and an
 * explicit {@link RowMapper}. Reads are keyed by {@code definition_name}; authoring replaces a
 * whole definition's case set in one transaction ({@link #replaceAll}).
 */
public class DslDefinitionTestRepository {

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
  private final ExtendedSelectQueryExecutor dslQueries;
  private static final DslDefinitionTestTableColumns T = DslDefinitionTestTableColumns.of();

  public DslDefinitionTestRepository(NamedParameterJdbcTemplate jdbcTemplate,
          TransactionTemplate transactionTemplate, ExtendedSelectQueryExecutor dslQueries) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
    this.dslQueries = Objects.requireNonNull(dslQueries);
  }

  public List<DslDefinitionTestEntity> listForDefinition(String definitionName) {
    var r = T.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .select(r.get(T.id()), r.get(T.definitionName()), r.get(T.caseName()),
                    r.get(T.input()), r.get(T.expectedOutput()), r.get(T.createdAt()),
                    r.get(T.updatedAt()))
            .where(Criteria.equal(r.get(T.definitionName()), Literal.of(definitionName)))
            .orderByAsc(r.get(T.caseName()))
            .build();
    return dslQueries.query(query, ROW_MAPPER);
  }

  public int countByDefinition(String definitionName) {
    var r = T.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .select(Literal.unsafe("COUNT(*)"))
            .where(Criteria.equal(r.get(T.definitionName()), Literal.of(definitionName)))
            .build();
    Long count = dslQueries.queryForObject(query, Long.class);
    return count != null ? count.intValue() : 0;
  }

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
