package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangeRequestQueryCriteriaTest {

  private final ChangeRequestTableColumns t = ChangeRequestTableColumns.of();
  private final TableReference r = t.refer();

  @Test
  void matchesDefinitionNameRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            ChangeRequestQueryCriteria.matchesDefinitionName(t, r, "MyFlow"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_change_request d
            WHERE
                d.definition_name = 'MyFlow'""");
  }

  @Test
  void matchesStatusRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            ChangeRequestQueryCriteria.matchesStatus(t, r, "PENDING"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_change_request d
            WHERE
                d.status = 'PENDING'""");
  }

  @Test
  void matchesIdRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            ChangeRequestQueryCriteria.matchesId(t, r, 7L));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_change_request d
            WHERE
                d.id = 7""");
  }

  @Test
  void fullSelectionCoversAllTableColumnsInOrder() {
    List<Selectable> selection = ChangeRequestQueryCriteria.fullSelection(t, r);
    assertThat(selection).hasSize(9);

    ExtendedSelectQuery query = new ExtendedSelectQuery();
    selection.forEach(query::addToSelection);
    query.addFrom(r);

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id,
                d.definition_name,
                d.draft_content,
                d.requested_by,
                d.requested_at,
                d.status,
                d.approved_by,
                d.approved_at,
                d.comment
            FROM
                dsl_change_request d""");
  }

  private ExtendedSelectQuery queryWithCriteria(
          com.github.squigglesql.squigglesql.criteria.Criteria criteria) {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(t.id()));
    query.addFrom(r);
    query.addCriteria(criteria);
    return query;
  }
}
