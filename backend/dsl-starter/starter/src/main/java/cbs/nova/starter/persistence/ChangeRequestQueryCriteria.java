package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Matchable;
import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.util.List;

final class ChangeRequestQueryCriteria {

  private ChangeRequestQueryCriteria() {
  }

  static Criteria matchesDefinitionName(ChangeRequestTableColumns t, TableReference r,
          String definitionName) {
    return Criteria.equal(r.get(t.definitionName()), Literal.of(definitionName));
  }

  static Criteria matchesStatus(ChangeRequestTableColumns t, TableReference r, String status) {
    return Criteria.equal(r.get(t.status()), Literal.of(status));
  }

  static Criteria matchesId(ChangeRequestTableColumns t, TableReference r, long id) {
    return Criteria.equal(r.get(t.id()), Literal.of(id));
  }

  static List<Selectable> fullSelection(ChangeRequestTableColumns t, TableReference r) {
    return List.of(
            r.get(t.id()),
            r.get(t.definitionName()),
            r.get(t.draftContent()),
            r.get(t.requestedBy()),
            r.get(t.requestedAt()),
            r.get(t.status()),
            r.get(t.approvedBy()),
            r.get(t.approvedAt()),
            r.get(t.comment()));
  }
}
