package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslDefinitionTestEntity;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC access to the {@code dsl_definition_tests} sidecar table (T409).
 *
 * <p>
 * Follows the {@link DslEventRepository} idioms: reads keyed by {@code definition_name} use
 * squigglesql; writes flow through the Spring Data {@link DslDefinitionTestCrudRepository}, and an
 * explicit {@link RowMapper}. Authoring replaces a whole definition's case set in one transaction
 * ({@link #replaceAll}).
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

  private final DslDefinitionTestCrudRepository crud;
  private final TransactionTemplate transactionTemplate;
  private final ExtendedSelectQueryExecutor dslQueries;
  private static final DslDefinitionTestTableColumns T = DslDefinitionTestTableColumns.of();

  public DslDefinitionTestRepository(DslDefinitionTestCrudRepository crud,
          TransactionTemplate transactionTemplate, ExtendedSelectQueryExecutor dslQueries) {
    this.crud = Objects.requireNonNull(crud);
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
    crud.save(new DslDefinitionTestEntity(
            row.id(),
            row.definitionName(),
            row.caseName(),
            row.inputJson(),
            row.expectedOutputJson(),
            row.createdAt(),
            row.updatedAt()));
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
    crud.deleteByDefinitionName(definitionName);
  }
}
