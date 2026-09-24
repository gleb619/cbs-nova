package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.util.List;

public class ApiKeyQueryCriteria {

  private ApiKeyQueryCriteria() {
  }

  public static Criteria matchesKeyHash(ApiKeyTableColumns t, TableReference r, String keyHash) {
    return Criteria.equal(r.get(t.keyHash()), Literal.of(keyHash));
  }

  public static Criteria isActive(ApiKeyTableColumns t, TableReference r) {
    return Criteria.isNull(r.get(t.revokedAt()));
  }

  public static Criteria matchesId(ApiKeyTableColumns t, TableReference r, long id) {
    return Criteria.equal(r.get(t.id()), Literal.of(id));
  }

  public static List<Selectable> fullSelection(ApiKeyTableColumns t, TableReference r) {
    return List.of(
            r.get(t.id()),
            r.get(t.label()),
            r.get(t.keyHash()),
            r.get(t.keyPrefix()),
            r.get(t.createdAt()),
            r.get(t.revokedAt()),
            r.get(t.lastUsedAt()));
  }
}
