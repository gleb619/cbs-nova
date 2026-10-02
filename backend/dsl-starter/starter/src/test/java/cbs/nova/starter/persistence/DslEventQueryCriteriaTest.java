package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DslEventQueryCriteriaTest {

  private final DslEventTableColumns t = DslEventTableColumns.of();
  private final TableReference r = t.refer();

  @Test
  void matchesEventTypeRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            DslEventQueryCriteria.matchesEventType(t, r, "ProcessStarted"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_events d
            WHERE
                d.event_type = 'ProcessStarted'""");
  }

  @Test
  void matchesAggregateTypeRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            DslEventQueryCriteria.matchesAggregateType(t, r, "Order"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_events d
            WHERE
                d.aggregate_type = 'Order'""");
  }

  @Test
  void matchesAggregateIdRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            DslEventQueryCriteria.matchesAggregateId(t, r, "order-42"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_events d
            WHERE
                d.aggregate_id = 'order-42'""");
  }

  @Test
  void matchesCorrelationIdRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            DslEventQueryCriteria.matchesCorrelationId(t, r, "corr-7"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_events d
            WHERE
                d.correlation_id = 'corr-7'""");
  }

  @Test
  void occurredSinceRendersGreaterOrEqualPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            DslEventQueryCriteria.occurredSince(t, r, Instant.parse("2024-01-15T10:30:00Z")));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_events d
            WHERE
                d.created_at >= '2024-01-15 10:30:00.000+00'""");
  }

  @Test
  void isUnpublishedRendersMqPublishedFalse() {
    ExtendedSelectQuery query = queryWithCriteria(
            DslEventQueryCriteria.isUnpublished(t, r));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_events d
            WHERE
                d.mq_published = false""");
  }

  @Test
  void fullSelectionCoversAllTableColumnsInOrder() {
    List<Selectable> selection = DslEventQueryCriteria.fullSelection(t, r);
    assertThat(selection).hasSize(8);

    ExtendedSelectQuery query = new ExtendedSelectQuery();
    selection.forEach(query::addToSelection);
    query.addFrom(r);

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id,
                d.event_type,
                d.aggregate_type,
                d.aggregate_id,
                d.correlation_id,
                d.payload,
                d.schema_version,
                d.created_at
            FROM
                dsl_events d""");
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
