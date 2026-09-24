package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.squigglesql.squigglesql.Table;
import com.github.squigglesql.squigglesql.TableColumn;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExtendedSelectQueryTest {

  private final Table users = new Table("users");
  private final TableColumn id = users.get("id");
  private final TableColumn name = users.get("name");
  private final TableColumn age = users.get("age");
  private final TableColumn status = users.get("status");
  private final TableReference r = users.refer();

  @Test
  void addToSelectionAppearsInSql() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addToSelection(r.get(name));
    query.addFrom(r);

    String sql = query.toString();

    assertThat(sql).contains("SELECT");
    assertThat(sql).contains(".id");
    assertThat(sql).contains(".name");
  }

  @Test
  void addFromAppearsInSql() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addFrom(r);

    String sql = query.toString();

    assertThat(sql).contains("FROM");
    assertThat(sql).contains("users");
  }

  @Test
  void addCriteriaAppearsInWhereClause() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addFrom(r);
    query.addCriteria(Criteria.equal(r.get(status), Literal.of("ACTIVE")));

    String sql = query.toString();

    assertThat(sql).contains("WHERE");
    assertThat(sql).contains("= 'ACTIVE'");
  }

  @Test
  void multipleCriteriaAreAnded() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addFrom(r);
    query.addCriteria(Criteria.equal(r.get(status), Literal.of("ACTIVE")));
    query.addCriteria(Criteria.greater(r.get(age), Literal.of(18)));

    String sql = query.toString();

    assertThat(sql).contains("WHERE");
    assertThat(sql).contains("= 'ACTIVE'");
    assertThat(sql).contains("AND");
    assertThat(sql).contains("> 18");
  }

  @Test
  void addOrderAppearsInOrderByClause() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addFrom(r);
    query.addOrder(r.get(name), true);

    String sql = query.toString();

    assertThat(sql).contains("ORDER BY");
    assertThat(sql).contains("ASC");
  }

  @Test
  void addOrderDescending() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addFrom(r);
    query.addOrder(r.get(id), false);

    String sql = query.toString();

    assertThat(sql).contains("ORDER BY");
    assertThat(sql).contains("DESC");
  }

  @Test
  void groupByAppearsInSql() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(status));
    query.addFrom(r);
    query.addGroupBy(r.get(status));

    String sql = query.toString();

    assertThat(sql).contains("GROUP BY");
    assertThat(sql).contains(".status");
  }

  @Test
  void limitAppearsInSql() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addFrom(r);
    query.limit(10);

    String sql = query.toString();

    assertThat(sql).contains("LIMIT 10");
  }

  @Test
  void offsetAppearsInSql() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addFrom(r);
    query.offset(20);

    String sql = query.toString();

    assertThat(sql).contains("OFFSET 20");
  }

  @Test
  void limitAndOffsetAppearInSql() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addFrom(r);
    query.limit(10);
    query.offset(20);

    String sql = query.toString();

    assertThat(sql).contains("LIMIT 10");
    assertThat(sql).contains("OFFSET 20");
  }

  @Test
  void collectTableReferencesDoesNotAddFromItemsDirectly() {
    Table orders = new Table("orders");
    TableReference ordersRef = orders.refer();

    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addToSelection(ordersRef.get(orders.get("order_id")));
    query.addFrom(r);
    query.addCriteria(Criteria.equal(r.get(id), ordersRef.get(orders.get("user_id"))));

    Set<TableReference> tables = new LinkedHashSet<>();
    query.collectTableReferences(tables);

    assertThat(tables).isEmpty();
  }

  @Test
  void tableReferencesFromSelectionsAndCriteriaAreGatheredInternally() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addFrom(r);
    query.addCriteria(Criteria.equal(r.get(status), Literal.of("ACTIVE")));

    String sql = query.toString();

    assertThat(sql).contains("users");
  }

  @Test
  void paginatedQuerySqlCorrectness() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addToSelection(r.get(name));
    query.addFrom(r);
    query.addCriteria(Criteria.equal(r.get(status), Literal.of("ACTIVE")));
    query.addOrder(r.get(name), true);
    query.limit(10);
    query.offset(20);

    String sql = query.toString();

    assertThat(sql).contains("SELECT");
    assertThat(sql).contains("FROM");
    assertThat(sql).contains("users");
    assertThat(sql).contains("WHERE");
    assertThat(sql).contains("= 'ACTIVE'");
    assertThat(sql).contains("ORDER BY");
    assertThat(sql).contains("ASC");
    assertThat(sql).contains("LIMIT 10");
    assertThat(sql).contains("OFFSET 20");
  }

  @Test
  void compileIsIdempotent() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addFrom(r);
    query.addCriteria(Criteria.equal(r.get(status), Literal.of("ACTIVE")));
    query.addOrder(r.get(name), true);
    query.limit(5);
    query.offset(10);

    String first = query.toString();
    String second = query.toString();

    assertThat(second).isEqualTo(first);
  }

  @Test
  void emptySelectionDefaultsToSelectOne() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addFrom(r);

    String sql = query.toString();

    assertThat(sql).contains("SELECT");
    assertThat(sql).contains("1");
  }

  @Test
  void limitZeroThrows() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();

    assertThatThrownBy(() -> query.limit(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Limit must be positive");
  }

  @Test
  void negativeLimitThrows() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();

    assertThatThrownBy(() -> query.limit(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Limit must be positive");
  }

  @Test
  void negativeOffsetThrows() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();

    assertThatThrownBy(() -> query.offset(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Offset must be non-negative");
  }

  @Test
  void nullSelectionThrows() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();

    assertThatThrownBy(() -> query.addToSelection(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Selection can not be null");
  }

  @Test
  void nullFromThrows() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();

    assertThatThrownBy(() -> query.addFrom(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("From item can not be null");
  }

  @Test
  void nullCriteriaThrows() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();

    assertThatThrownBy(() -> query.addCriteria(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Criteria can not be null");
  }

  @Test
  void nullGroupByThrows() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();

    assertThatThrownBy(() -> query.addGroupBy(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Group by expression can not be null");
  }

  @Test
  void nullOrderThrows() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();

    assertThatThrownBy(() -> query.addOrder(null, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ordering expression can not be null");
  }

  @Test
  void multipleOrderByClauses() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addFrom(r);
    query.addOrder(r.get(name), true);
    query.addOrder(r.get(age), false);

    String sql = query.toString();

    assertThat(sql).contains("ORDER BY");
    assertThat(sql).contains("ASC");
    assertThat(sql).contains("DESC");
  }

  @Test
  void countQueryRendersFromAliasNotLiteralNull() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addFrom(r);
    query.addToSelection(Literal.unsafe("COUNT(*)"));

    String sql = query.toString();

    assertThat(sql).doesNotContain("null");
    assertThat(sql).contains("COUNT(*)");
    assertThat(sql).contains("users u");
  }

  @Test
  void countQueryWithWhereRendersFromAliasNotLiteralNull() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addFrom(r);
    query.addToSelection(Literal.unsafe("COUNT(*)"));
    query.addCriteria(Criteria.equal(r.get(status), Literal.of("ACTIVE")));

    String sql = query.toString();

    assertThat(sql).doesNotContain("null");
    assertThat(sql).contains("COUNT(*)");
    assertThat(sql).contains("users u");
  }

  @Test
  void fullQueryWithAllClauses() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(id));
    query.addToSelection(r.get(name));
    query.addFrom(r);
    query.addCriteria(Criteria.greater(r.get(age), Literal.of(18)));
    query.addGroupBy(r.get(status));
    query.addOrder(r.get(name), true);
    query.limit(25);
    query.offset(50);

    String sql = query.toString();

    assertThat(sql).contains("SELECT");
    assertThat(sql).contains("FROM");
    assertThat(sql).contains("WHERE");
    assertThat(sql).contains("GROUP BY");
    assertThat(sql).contains("ORDER BY");
    assertThat(sql).contains("LIMIT 25");
    assertThat(sql).contains("OFFSET 50");
  }
}
