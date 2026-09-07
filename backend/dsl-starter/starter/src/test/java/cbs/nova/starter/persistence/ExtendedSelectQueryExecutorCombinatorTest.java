package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.squigglesql.squigglesql.Table;
import com.github.squigglesql.squigglesql.TableColumn;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import org.junit.jupiter.api.Test;

class ExtendedSelectQueryExecutorCombinatorTest {

  private final ExtendedSelectQueryExecutor executor = new ExtendedSelectQueryExecutor(null);

  @Test
  void andCombinesAccumulatedCriteriaWithAnd() {
    QueryParts q = parts();

    String sql = executor.select()
        .from(q.r)
        .select(q.r.get(q.id))
        .where(Criteria.equal(q.r.get(q.id), Literal.of(1)))
        .and(Criteria.equal(q.r.get(q.status), Literal.of("RUNNING")))
        .sql();

    assertThat(sql).contains("WHERE")
                   .contains("(")
                   .contains("d.id = 1 AND")
                   .contains("d.status = 'RUNNING'")
                   .contains(")");
  }

  @Test
  void orCombinesAccumulatedCriteriaWithOr() {
    QueryParts q = parts();

    String sql = executor.select()
        .from(q.r)
        .select(q.r.get(q.id))
        .where(Criteria.equal(q.r.get(q.id), Literal.of(1)))
        .or(Criteria.equal(q.r.get(q.status), Literal.of("RUNNING")))
        .sql();

    assertThat(sql).contains("WHERE")
                   .contains("(")
                   .contains("d.id = 1 OR")
                   .contains("d.status = 'RUNNING'")
                   .contains(")");
  }

  @Test
  void andIfTrueComposes() {
    QueryParts q = parts();

    String sql = executor.select()
        .from(q.r)
        .select(q.r.get(q.id))
        .where(Criteria.equal(q.r.get(q.id), Literal.of(1)))
        .andIf(true, () -> Criteria.equal(q.r.get(q.status), Literal.of("RUNNING")))
        .sql();

    assertThat(sql).contains("d.id = 1 AND")
                   .contains("d.status = 'RUNNING'");
  }

  @Test
  void andIfFalseIsNoOp() {
    QueryParts q = parts();

    String sql = executor.select()
        .from(q.r)
        .select(q.r.get(q.id))
        .where(Criteria.equal(q.r.get(q.id), Literal.of(1)))
        .andIf(false, () -> Criteria.equal(q.r.get(q.status), Literal.of("RUNNING")))
        .sql();

    assertThat(sql).contains("d.id = 1");
    assertThat(sql).doesNotContain("d.status");
  }

  @Test
  void orIfTrueComposes() {
    QueryParts q = parts();

    String sql = executor.select()
        .from(q.r)
        .select(q.r.get(q.id))
        .where(Criteria.equal(q.r.get(q.id), Literal.of(1)))
        .orIf(true, () -> Criteria.equal(q.r.get(q.status), Literal.of("RUNNING")))
        .sql();

    assertThat(sql).contains("d.id = 1 OR")
                   .contains("d.status = 'RUNNING'");
  }

  @Test
  void orIfFalseIsNoOp() {
    QueryParts q = parts();

    String sql = executor.select()
        .from(q.r)
        .select(q.r.get(q.id))
        .where(Criteria.equal(q.r.get(q.id), Literal.of(1)))
        .orIf(false, () -> Criteria.equal(q.r.get(q.status), Literal.of("RUNNING")))
        .sql();

    assertThat(sql).contains("d.id = 1");
    assertThat(sql).doesNotContain("d.status");
  }

  @Test
  void leftAssociativePrecedenceAndThenOr() {
    QueryParts q = parts();

    String sql = executor.select()
        .from(q.r)
        .select(q.r.get(q.id))
        .where(Criteria.equal(q.r.get(q.id), Literal.of(1)))
        .and(Criteria.equal(q.r.get(q.status), Literal.of("RUNNING")))
        .or(Criteria.equal(q.r.get(q.processName), Literal.of("proc")))
        .sql();

    // Logical grouping: (id = 1 AND status = 'RUNNING') OR process_name = 'proc'
    assertThat(sql).contains("d.id = 1 AND")
                   .contains("d.status = 'RUNNING'")
                   .contains("OR")
                   .contains("d.process_name = 'proc'");
  }

  @Test
  void andWithNoAccumulatedCriteriaBehavesLikeWhere() {
    QueryParts q = parts();

    String sql = executor.select()
        .from(q.r)
        .select(q.r.get(q.id))
        .and(Criteria.equal(q.r.get(q.id), Literal.of(1)))
        .sql();

    assertThat(sql).isEqualTo("""
        SELECT
            d.id
        FROM
            dsl_run d
        WHERE
            d.id = 1""");
  }

  @Test
  void orWithNoAccumulatedCriteriaBehavesLikeWhere() {
    QueryParts q = parts();

    String sql = executor.select()
        .from(q.r)
        .select(q.r.get(q.id))
        .or(Criteria.equal(q.r.get(q.id), Literal.of(1)))
        .sql();

    assertThat(sql).isEqualTo("""
        SELECT
            d.id
        FROM
            dsl_run d
        WHERE
            d.id = 1""");
  }

  private record QueryParts(TableReference r, TableColumn id, TableColumn status, TableColumn processName) {
  }

  private static QueryParts parts() {
    Table table = new Table("dsl_run");
    TableColumn id = table.get("id");
    TableColumn status = table.get("status");
    TableColumn processName = table.get("process_name");
    return new QueryParts(table.refer(), id, status, processName);
  }
}
