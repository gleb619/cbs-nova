package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationRuleFiringQueryCriteriaTest {

  private final NotificationRuleFiringTableColumns t = NotificationRuleFiringTableColumns.of();
  private final TableReference r = t.refer();

  @Test
  void matchesRuleIdRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            NotificationRuleFiringQueryCriteria.matchesRuleId(t, r, 99L));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_notification_rule_firing d
            WHERE
                d.rule_id = 99""");
  }

  @Test
  void fullSelectionCoversAllTableColumnsInOrder() {
    List<Selectable> selection = NotificationRuleFiringQueryCriteria.fullSelection(t, r);
    assertThat(selection).hasSize(9);

    ExtendedSelectQuery query = new ExtendedSelectQuery();
    selection.forEach(query::addToSelection);
    query.addFrom(r);

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id,
                d.event_id,
                d.rule_id,
                d.rule_name,
                d.sink,
                d.outcome,
                d.detail,
                d.duration_ms,
                d.created_at
            FROM
                dsl_notification_rule_firing d""");
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
