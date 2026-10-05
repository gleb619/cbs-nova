package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.NotificationRuleFiringEntity;
import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC access to the append-only {@code dsl_notification_rule_firing} audit table: an insert plus a
 * paged, filterable read, plus a single, narrowly-named maintenance exception:
 * {@link #purgeOlderThan(Instant, int)}. The maintenance method exists solely so the opt-in
 * {@code dsl.maintenance.notification-firing-retention} purge (T814) can age rows out of the table
 * once an operator has explicitly enabled it. It is not exposed by any REST handler and not
 * reachable from any controller code path.
 *
 * <p>
 * Selects are built with squigglesql ({@link ExtendedSelectQueryExecutor}); the insert flows
 * through the Spring Data {@link NotificationRuleFiringCrudRepository}.
 */
@RequiredArgsConstructor
public class NotificationRuleFiringRepository {

  private static final RowMapper<NotificationRuleFiringEntity> ROW_MAPPER = (rs,
          rowNum) -> new NotificationRuleFiringEntity(
                  rs.getLong("id"),
                  rs.getLong("event_id"),
                  rs.getLong("rule_id"),
                  rs.getString("rule_name"),
                  rs.getString("sink"),
                  rs.getString("outcome"),
                  rs.getString("detail"),
                  rs.getObject("duration_ms", Long.class),
                  rs.getTimestamp("created_at").toInstant());

  private final NotificationRuleFiringCrudRepository crud;
  private final ExtendedSelectQueryExecutor dslQueries;

  public long insert(NotificationRuleFiringEntity row) {
    Objects.requireNonNull(row, "row");
    return crud.save(row).id();
  }

  public NotificationRuleFiringSearchResult search(@Nullable Long ruleId, int offset, int limit) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative, was " + offset);
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive, was " + limit);
    }

    NotificationRuleFiringTableColumns t = NotificationRuleFiringTableColumns.of();
    TableReference r = t.refer();

    ExtendedSelectQuery countQuery = dslQueries.select()
            .from(r)
            .selectCount()
            .whereIf(ruleId != null,
                    () -> NotificationRuleFiringQueryCriteria.matchesRuleId(t, r, ruleId))
            .build();
    long total = dslQueries.queryForObject(countQuery, Long.class);

    ExtendedSelectQuery dataQuery = dslQueries.select()
            .from(r)
            .select(NotificationRuleFiringQueryCriteria.fullSelection(t, r))
            .whereIf(ruleId != null,
                    () -> NotificationRuleFiringQueryCriteria.matchesRuleId(t, r, ruleId))
            .orderByDesc(r.get(t.createdAt()))
            .orderByDesc(r.get(t.id()))
            .limit(limit)
            .offset(offset)
            .build();
    List<NotificationRuleFiringEntity> items = dslQueries.query(dataQuery, ROW_MAPPER);
    return new NotificationRuleFiringSearchResult(items, total);
  }

  /**
   * Maintenance-only: deletes at most {@code batchSize} rows whose {@code created_at} is strictly
   * older than {@code cutoff}. Returns the number of rows actually deleted.
   *
   * <p>
   * Sole deletion path for the {@code dsl_notification_rule_firing} table; see the class Javadoc.
   * Callers are expected to invoke this in a loop until it returns less than {@code batchSize}.
   */
  public int purgeOlderThan(Instant cutoff, int batchSize) {
    if (cutoff == null) {
      throw new IllegalArgumentException("cutoff must not be null");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
    }
    return crud.deleteOlderThan(cutoff, batchSize);
  }
}
