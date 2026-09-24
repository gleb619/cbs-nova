package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.FromItem;
import com.github.squigglesql.squigglesql.Matchable;
import com.github.squigglesql.squigglesql.Output;
import com.github.squigglesql.squigglesql.QueryCompiler;
import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.alias.AliasGenerator;
import com.github.squigglesql.squigglesql.alias.Alphabet;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.query.Query;
import com.github.squigglesql.squigglesql.statement.StatementBuilder;
import com.github.squigglesql.squigglesql.statement.StatementCompiler;
import com.github.squigglesql.squigglesql.util.CollectionWriter;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SquigglesQL select query extended with {@code GROUP BY}, {@code LIMIT} and {@code OFFSET} clauses
 * that the stock {@link com.github.squigglesql.squigglesql.query.SelectQuery} doesn't support.
 * Mirrors the stock query compilation logic and appends the extra clauses in standard SQL order.
 *
 * @see <a href="https://github.com/squigglesql/squigglesql">Docs</a>
 *
 */
public class ExtendedSelectQuery extends Query implements Matchable {

  private static final Alphabet TABLE_REFERENCE_ALIAS_ALPHABET = new Alphabet('t', 7);

  private final List<Selectable> selection = new ArrayList<>();
  private final List<FromItem> fromItems = new ArrayList<>();
  private final List<Criteria> criterias = new ArrayList<>();
  private final List<QueryOrder> orders = new ArrayList<>();
  private final List<Matchable> groupBy = new ArrayList<>();
  private Integer limit;
  private Integer offset;

  public void addToSelection(Selectable selectable) {
    if (selectable == null) {
      throw new IllegalArgumentException("Selection can not be null.");
    }
    selection.add(selectable);
  }

  public void addFrom(FromItem fromItem) {
    if (fromItem == null) {
      throw new IllegalArgumentException("From item can not be null.");
    }
    fromItems.add(fromItem);
  }

  public void addCriteria(Criteria criteria) {
    if (criteria == null) {
      throw new IllegalArgumentException("Criteria can not be null.");
    }
    this.criterias.add(criteria);
  }

  public void addOrder(Selectable selectable, boolean ascending) {
    if (selectable == null) {
      throw new IllegalArgumentException("Ordering expression can not be null.");
    }
    this.orders.add(new QueryOrder(selectable, ascending));
  }

  public void addGroupBy(Matchable expression) {
    if (expression == null) {
      throw new IllegalArgumentException("Group by expression can not be null.");
    }
    this.groupBy.add(expression);
  }

  public void limit(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("Limit must be positive, was " + limit);
    }
    this.limit = limit;
  }

  public void offset(int offset) {
    if (offset < 0) {
      throw new IllegalArgumentException("Offset must be non-negative, was " + offset);
    }
    this.offset = offset;
  }

  @Override
  public void collectTableReferences(Set<TableReference> tables) {
  }

  @Override
  public void compile(QueryCompiler compiler) {
    compiler.writeln('(').indent();
    compile(compiler.getOutput());
    compiler.writeln().unindent().write(')');
  }

  @Override
  protected void compile(Output output) {
    Set<TableReference> usedTableReferences = findUsedTableReferences();
    Set<FromItem> allFromItems = new LinkedHashSet<>(fromItems);
    allFromItems.addAll(usedTableReferences);

    // Generate aliases for every TableReference the compiled SQL will render:
    // not only those collected from selection/criteria/groupBy/orders, but also
    // those reachable from explicit FROM items. Missing aliases previously
    // caused `QueryCompiler#getAlias` to return null and render the literal
    // "null" as the table alias (e.g. "SELECT COUNT(*) FROM dsl_x null").
    Set<TableReference> aliasedTableReferences = new LinkedHashSet<>(usedTableReferences);
    for (FromItem item : fromItems) {
      item.collectTableReferences(aliasedTableReferences);
    }

    QueryCompiler compiler = new QueryCompiler(output,
            AliasGenerator.generateAliases(aliasedTableReferences, TABLE_REFERENCE_ALIAS_ALPHABET));

    compiler.write("SELECT");
    if (selection.isEmpty()) {
      compiler.writeln(" 1");
    } else {
      CollectionWriter.writeCollection(compiler, selection, ",", false, true);
    }

    if (!allFromItems.isEmpty()) {
      compiler.write("FROM");
      CollectionWriter.writeCollection(compiler, allFromItems, ",", false, true);
    }

    if (!criterias.isEmpty()) {
      compiler.write("WHERE");
      CollectionWriter.writeCollection(compiler, criterias, " AND", false, true);
    }

    if (!groupBy.isEmpty()) {
      compiler.write("GROUP BY");
      CollectionWriter.writeCollection(compiler, groupBy, ",", false, true);
    }

    if (!orders.isEmpty()) {
      compiler.write("ORDER BY");
      compiler.writeln();
      for (int i = 0; i < orders.size(); i++) {
        if (i > 0) {
          compiler.write(',');
          compiler.writeln();
        }
        QueryOrder order = orders.get(i);
        order.selectable().compile(compiler);
        compiler.write(order.ascending() ? " ASC" : " DESC");
      }
    }

    if (limit != null) {
      compiler.write(" LIMIT " + limit);
    }
    if (offset != null) {
      compiler.write(" OFFSET " + offset);
    }
  }

  @Override
  protected <S> StatementBuilder<S> createStatementBuilder(StatementCompiler<S> compiler,
          String query)
          throws SQLException {
    return compiler.createStatementBuilder(query);
  }

  private Set<TableReference> findUsedTableReferences() {
    Set<TableReference> references = new LinkedHashSet<>();
    for (Selectable selectable : selection) {
      selectable.collectTableReferences(references);
    }
    for (Criteria criteria : criterias) {
      criteria.collectTableReferences(references);
    }
    for (Matchable expression : groupBy) {
      expression.collectTableReferences(references);
    }
    for (QueryOrder order : orders) {
      order.selectable().collectTableReferences(references);
    }
    return references;
  }

  private record QueryOrder(Selectable selectable, boolean ascending) {
  }
}
