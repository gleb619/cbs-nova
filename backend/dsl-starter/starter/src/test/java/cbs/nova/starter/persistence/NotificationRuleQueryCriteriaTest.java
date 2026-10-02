package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationRuleQueryCriteriaTest {

  private final NotificationRuleTableColumns t = NotificationRuleTableColumns.of();
  private final TableReference r = t.refer();

  @Test
  void matchesIdRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            NotificationRuleQueryCriteria.matchesId(t, r, 42L));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_notification_rule d
            WHERE
                d.id = 42""");
  }

  @Test
  void isEnabledRendersEnabledTrue() {
    ExtendedSelectQuery query = queryWithCriteria(
            NotificationRuleQueryCriteria.isEnabled(t, r));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_notification_rule d
            WHERE
                d.enabled = true""");
  }

  @Test
  void matchesEventTypeRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            NotificationRuleQueryCriteria.matchesEventType(t, r, "OrderCompleted"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_notification_rule d
            WHERE
                d.event_type = 'OrderCompleted'""");
  }

  @Test
  void matchesEventTypeEscapesEmbeddedSingleQuote() {
    ExtendedSelectQuery query = queryWithCriteria(
            NotificationRuleQueryCriteria.matchesEventType(t, r, "Order's Event"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_notification_rule d
            WHERE
                d.event_type = 'Order''s Event'""");
  }

  @Test
  void fullSelectionCoversAllTableColumnsInOrder() {
    List<Selectable> selection = NotificationRuleQueryCriteria.fullSelection(t, r);
    assertThat(selection).hasSize(13);

    ExtendedSelectQuery query = new ExtendedSelectQuery();
    selection.forEach(query::addToSelection);
    query.addFrom(r);

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id,
                d.name,
                d.enabled,
                d.event_type,
                d.aggregate_type,
                d.aggregate_id_pattern,
                d.definition_pattern,
                d.status,
                d.actions,
                d.priority,
                d.rate_class,
                d.created_at,
                d.updated_at
            FROM
                dsl_notification_rule d""");
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
