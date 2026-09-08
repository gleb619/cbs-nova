package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.squigglesql.squigglesql.Table;
import com.github.squigglesql.squigglesql.TableColumn;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import org.junit.jupiter.api.Test;

/**
 * Regression tests that pin the SQL produced by chained {@code where(...)} / {@code whereIf(...)}.
 * The strings are the compatibility contract: any refactor must keep them byte-identical.
 */
class ExtendedSelectQueryExecutorSqlPinTest {

  // Closing delimiter is on the last content line so there is NO trailing newline.
  private static final String PINNED_TWO_WHERE_SQL = """
          SELECT
              d.id
          FROM
              dsl_run d
          WHERE
              d.id = 1 AND
              d.status = 'RUNNING'""";

  private static final String PINNED_WHERE_IF_WHERE_SQL = """
          SELECT
              d.id
          FROM
              dsl_run d
          WHERE
              d.id = 1 AND
              d.status = 'RUNNING'""";

  private final ExtendedSelectQueryExecutor executor = new ExtendedSelectQueryExecutor(null);

  @Test
  void twoWhereCallsProduceImplicitAndSql() {
    Table table = new Table("dsl_run");
    TableColumn id = table.get("id");
    TableColumn status = table.get("status");
    TableReference r = table.refer();

    String sql = executor.select()
            .from(r)
            .select(r.get(id))
            .where(Criteria.equal(r.get(id), Literal.of(1)))
            .where(Criteria.equal(r.get(status), Literal.of("RUNNING")))
            .sql();

    assertThat(sql).isEqualTo(PINNED_TWO_WHERE_SQL);
  }

  @Test
  void whereIfTrueFollowedByWhereProducesImplicitAndSql() {
    Table table = new Table("dsl_run");
    TableColumn id = table.get("id");
    TableColumn status = table.get("status");
    TableReference r = table.refer();

    String sql = executor.select()
            .from(r)
            .select(r.get(id))
            .whereIf(true, () -> Criteria.equal(r.get(id), Literal.of(1)))
            .where(Criteria.equal(r.get(status), Literal.of("RUNNING")))
            .sql();

    assertThat(sql).isEqualTo(PINNED_WHERE_IF_WHERE_SQL);
  }

  @Test
  void explicitAndWrapsCriteriaInParentheses() {
    Table table = new Table("dsl_run");
    TableColumn id = table.get("id");
    TableColumn status = table.get("status");
    TableReference r = table.refer();

    String sql = executor.select()
            .from(r)
            .select(r.get(id))
            .where(Criteria.equal(r.get(id), Literal.of(1)))
            .and(Criteria.equal(r.get(status), Literal.of("RUNNING")))
            .sql();

    // Explicit composition uses Criteria.and(...) which renders parentheses.
    assertThat(sql).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_run d
            WHERE
                (
                    d.id = 1 AND
                    d.status = 'RUNNING'
                )""");
  }
}
