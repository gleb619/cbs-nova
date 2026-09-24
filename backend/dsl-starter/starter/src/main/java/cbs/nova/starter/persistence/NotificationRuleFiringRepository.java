package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.NotificationRuleFiringEntity;
import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC access to the append-only {@code dsl_notification_rule_firing} audit table: an insert plus a
 * paged, filterable read. There is intentionally no update or delete path.
 *
 * <p>
 * Selects are built with squigglesql ({@link ExtendedSelectQueryExecutor}); the insert flows
 * through the Spring Data {@link NotificationRuleFiringCrudRepository}.
 */
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

  public NotificationRuleFiringRepository(NotificationRuleFiringCrudRepository crud,
          ExtendedSelectQueryExecutor dslQueries) {
    this.crud = crud;
    this.dslQueries = dslQueries;
  }

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
            .select(Literal.unsafe("COUNT(*)"))
            .whereIf(ruleId != null,
                    () -> NotificationRuleFiringQueryCriteria.matchesRuleId(t, r, ruleId))
            .build();
    long total = dslQueries.queryForObject(countQuery, Long.class);

    ExtendedSelectQuery dataQuery = dslQueries.select()
            .from(r)
            .select(NotificationRuleFiringQueryCriteria.fullSelection(t, r)
                    .toArray(Selectable[]::new))
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
}
