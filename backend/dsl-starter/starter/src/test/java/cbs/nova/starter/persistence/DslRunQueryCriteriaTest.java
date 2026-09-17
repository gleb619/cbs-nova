package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import java.util.List;
import org.junit.jupiter.api.Test;

class DslRunQueryCriteriaTest {

  private final DslRunTableColumns t = DslRunTableColumns.of("dsl_runs");
  private final TableReference r = t.refer();

  @Test
  void matchesProcessNameRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            DslRunQueryCriteria.matchesProcessName(t, r, "MyProcess"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_runs d
            WHERE
                d.process_name = 'MyProcess'""");
  }

  @Test
  void matchesStatusIgnoreCaseRendersLowerOnBothSides() {
    ExtendedSelectQuery query = queryWithCriteria(
            DslRunQueryCriteria.matchesStatusIgnoreCase(t, r, "COMPLETED"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_runs d
            WHERE
                LOWER(d.status) = LOWER('COMPLETED')""");
  }

  @Test
  void matchesModeIgnoreCaseRendersNullIfCoalesceLowerChain() {
    ExtendedSelectQuery query = queryWithCriteria(
            DslRunQueryCriteria.matchesModeIgnoreCase(t, r, "RUN"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_runs d
            WHERE
                LOWER(COALESCE(NULLIF(d.execution_mode, ''), 'RUN')) = LOWER('RUN')""");
  }

  @Test
  void matchesCorrelationIdRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            DslRunQueryCriteria.matchesCorrelationId(t, r, "corr-123"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_runs d
            WHERE
                d.correlation_id = 'corr-123'""");
  }

  @Test
  void fullSelectionCoversAllTableColumnsInOrder() {
    List<Selectable> selection = DslRunQueryCriteria.fullSelection(t, r);
    assertThat(selection).hasSize(14);

    ExtendedSelectQuery query = new ExtendedSelectQuery();
    selection.forEach(query::addToSelection);
    query.addFrom(r);

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id,
                d.run_id,
                d.process_name,
                d.status,
                d.input_json,
                d.output_json,
                d.error_message,
                d.context_json,
                d.started_at,
                d.finished_at,
                d.execution_mode,
                d.triggered_by,
                d.correlation_id,
                d.definition_hash
            FROM
                dsl_runs d""");
  }

  @Test
  void minuteBucketRendersDateTruncMinuteOnStartedAt() {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(DslRunQueryCriteria.minuteBucket(t, r));
    query.addFrom(r);

    assertThat(query.toString()).isEqualTo("""
            SELECT
                date_trunc('minute', d.started_at)
            FROM
                dsl_runs d""");
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
