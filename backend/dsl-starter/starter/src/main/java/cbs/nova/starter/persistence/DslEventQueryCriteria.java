package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.Matchable;
import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.util.List;

public class DslEventQueryCriteria {

  private DslEventQueryCriteria() {
  }

  public static Criteria matchesEventType(DslEventTableColumns t, TableReference r,
          String eventType) {
    return Criteria.equal(r.get(t.eventType()), Literal.of(eventType));
  }

  public static Criteria matchesAggregateType(DslEventTableColumns t, TableReference r,
          String aggregateType) {
    return Criteria.equal(r.get(t.aggregateType()), Literal.of(aggregateType));
  }

  public static Criteria matchesAggregateId(DslEventTableColumns t, TableReference r,
          String aggregateId) {
    return Criteria.equal(r.get(t.aggregateId()), Literal.of(aggregateId));
  }

  public static Criteria matchesCorrelationId(DslEventTableColumns t, TableReference r,
          String correlationId) {
    return Criteria.equal(r.get(t.correlationId()), Literal.of(correlationId));
  }

  public static Criteria occurredSince(DslEventTableColumns t, TableReference r,
          java.time.Instant since) {
    return Criteria.notLess(r.get(t.createdAt()), Literal.of(since));
  }

  public static Criteria isUnpublished(DslEventTableColumns t, TableReference r) {
    return Criteria.equal(r.get(t.mqPublished()), Literal.of(false));
  }

  public static List<Selectable> fullSelection(DslEventTableColumns t, TableReference r) {
    return List.of(
            r.get(t.id()),
            r.get(t.eventType()),
            r.get(t.aggregateType()),
            r.get(t.aggregateId()),
            r.get(t.correlationId()),
            r.get(t.payload()),
            r.get(t.schemaVersion()),
            r.get(t.createdAt()));
  }
}
