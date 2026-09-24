package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.util.List;

public final class WebhookDeliveryQueryCriteria {

  public WebhookDeliveryQueryCriteria() {
  }

  public static Criteria matchesSubscriptionId(WebhookDeliveryTableColumns t, TableReference r,
          String subscriptionId) {
    return Criteria.equal(r.get(t.subscriptionId()), Literal.of(subscriptionId));
  }

  public static List<Selectable> fullSelection(WebhookDeliveryTableColumns t, TableReference r) {
    return List.of(
            r.get(t.id()),
            r.get(t.occurredAt()),
            r.get(t.subscriptionId()),
            r.get(t.eventType()),
            r.get(t.url()),
            r.get(t.status()),
            r.get(t.attempts()),
            r.get(t.lastError()),
            r.get(t.durationMs()));
  }
}
