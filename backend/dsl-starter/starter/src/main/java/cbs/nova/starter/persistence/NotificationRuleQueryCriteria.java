package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.util.List;

public class NotificationRuleQueryCriteria {

  private NotificationRuleQueryCriteria() {
  }

  public static Criteria matchesId(NotificationRuleTableColumns t, TableReference r, long id) {
    return Criteria.equal(r.get(t.id()), Literal.of(id));
  }

  public static Criteria isEnabled(NotificationRuleTableColumns t, TableReference r) {
    return Criteria.equal(r.get(t.enabled()), Literal.of(true));
  }

  public static Criteria matchesEventType(NotificationRuleTableColumns t, TableReference r,
          String eventType) {
    return Criteria.equal(r.get(t.eventType()), Literal.of(eventType));
  }

  public static List<Selectable> fullSelection(NotificationRuleTableColumns t, TableReference r) {
    return List.of(
            r.get(t.id()),
            r.get(t.name()),
            r.get(t.enabled()),
            r.get(t.eventType()),
            r.get(t.aggregateType()),
            r.get(t.aggregateIdPattern()),
            r.get(t.definitionPattern()),
            r.get(t.status()),
            r.get(t.actions()),
            r.get(t.priority()),
            r.get(t.rateClass()),
            r.get(t.createdAt()),
            r.get(t.updatedAt()));
  }
}
