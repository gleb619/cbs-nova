package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebhookDeliveryQueryCriteriaTest {

  private final WebhookDeliveryTableColumns t = WebhookDeliveryTableColumns.of();
  private final TableReference r = t.refer();

  @Test
  void matchesSubscriptionIdRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            WebhookDeliveryQueryCriteria.matchesSubscriptionId(t, r, "sub-42"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_webhook_deliveries d
            WHERE
                d.subscription_id = 'sub-42'""");
  }

  @Test
  void fullSelectionCoversAllTableColumnsInOrder() {
    List<Selectable> selection = WebhookDeliveryQueryCriteria.fullSelection(t, r);
    assertThat(selection).hasSize(9);

    ExtendedSelectQuery query = new ExtendedSelectQuery();
    selection.forEach(query::addToSelection);
    query.addFrom(r);

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id,
                d.occurred_at,
                d.subscription_id,
                d.event_type,
                d.url,
                d.status,
                d.attempts,
                d.last_error,
                d.duration_ms
            FROM
                dsl_webhook_deliveries d""");
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
