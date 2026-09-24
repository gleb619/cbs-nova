package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.util.List;

public class NotificationRuleFiringQueryCriteria {

  private NotificationRuleFiringQueryCriteria() {
  }

  public static Criteria matchesRuleId(NotificationRuleFiringTableColumns t, TableReference r,
          Long ruleId) {
    return Criteria.equal(r.get(t.ruleId()), Literal.of(ruleId));
  }

  public static List<Selectable> fullSelection(NotificationRuleFiringTableColumns t,
          TableReference r) {
    return List.of(
            r.get(t.id()),
            r.get(t.eventId()),
            r.get(t.ruleId()),
            r.get(t.ruleName()),
            r.get(t.sink()),
            r.get(t.outcome()),
            r.get(t.detail()),
            r.get(t.durationMs()),
            r.get(t.createdAt()));
  }
}
