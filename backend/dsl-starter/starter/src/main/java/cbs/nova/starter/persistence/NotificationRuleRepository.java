package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.NotificationRuleEntity;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC access to {@code dsl_notification_rule}. Follows the
 * {@link cbs.nova.starter.persistence.JdbcDslRunRepository} idioms: reads via squigglesql, writes
 * delegated to the Spring Data {@link NotificationRuleCrudRepository}, and an explicit
 * {@link RowMapper}.
 */
public class NotificationRuleRepository {

  private static final RowMapper<NotificationRuleEntity> ROW_MAPPER = (rs,
          rowNum) -> new NotificationRuleEntity(
                  rs.getLong("id"),
                  rs.getString("name"),
                  rs.getBoolean("enabled"),
                  rs.getString("event_type"),
                  rs.getString("aggregate_type"),
                  rs.getString("aggregate_id_pattern"),
                  rs.getString("definition_pattern"),
                  rs.getString("status"),
                  rs.getString("actions"),
                  rs.getInt("priority"),
                  rs.getString("rate_class"),
                  rs.getTimestamp("created_at").toInstant(),
                  rs.getTimestamp("updated_at").toInstant());

  private final NotificationRuleCrudRepository crud;
  private final ExtendedSelectQueryExecutor dslQueries;
  private static final NotificationRuleTableColumns T = NotificationRuleTableColumns.of();

  public NotificationRuleRepository(NotificationRuleCrudRepository crud,
          ExtendedSelectQueryExecutor dslQueries) {
    this.crud = Objects.requireNonNull(crud);
    this.dslQueries = Objects.requireNonNull(dslQueries);
  }

  public long insert(NotificationRuleEntity row) {
    Objects.requireNonNull(row, "row");
    NotificationRuleEntity saved = crud.save(new NotificationRuleEntity(
            row.id(),
            row.name(),
            row.enabled(),
            row.eventType(),
            row.aggregateType(),
            row.aggregateIdPattern(),
            row.definitionPattern(),
            row.status(),
            row.actionsJson(),
            row.priority(),
            row.rateClass(),
            row.createdAt(),
            row.updatedAt()));
    return saved.id();
  }

  public void update(NotificationRuleEntity row) {
    Objects.requireNonNull(row, "row");
    if (row.id() == null) {
      throw new IllegalArgumentException("Cannot update a rule without an id");
    }
    crud.update(
            row.id(),
            row.name(),
            row.enabled(),
            row.eventType(),
            row.aggregateType(),
            row.aggregateIdPattern(),
            row.definitionPattern(),
            row.status(),
            row.actionsJson(),
            row.priority(),
            row.rateClass(),
            row.updatedAt());
  }

  public Optional<NotificationRuleEntity> findById(long id) {
    var r = T.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .select(NotificationRuleQueryCriteria.fullSelection(T, r))
            .where(NotificationRuleQueryCriteria.matchesId(T, r, id))
            .build();
    List<NotificationRuleEntity> items = dslQueries.query(query, ROW_MAPPER);
    return items.stream().findFirst();
  }

  public NotificationRuleSearchResult findAll(int offset, int limit) {
    checkPagination(offset, limit);
    var r = T.refer();
    ExtendedSelectQuery countQuery = dslQueries.select()
            .selectCount()
            .from(r)
            .build();
    long total = dslQueries.queryForObject(countQuery, Long.class);
    ExtendedSelectQuery dataQuery = dslQueries.select()
            .select(NotificationRuleQueryCriteria.fullSelection(T, r))
            .from(r)
            .orderByDesc(r.get(T.priority()))
            .orderByAsc(r.get(T.id()))
            .limit(limit)
            .offset(offset)
            .build();
    List<NotificationRuleEntity> items = dslQueries.query(dataQuery, ROW_MAPPER);
    return new NotificationRuleSearchResult(items, total);
  }

  /**
   * The engine's hot lookup: all enabled rules for an exact event type, in match order (priority
   * desc, id asc). Finer filter dimensions are matched in memory by the engine.
   */
  public List<NotificationRuleEntity> findMatchingEnabled(String eventType) {
    var r = T.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .select(NotificationRuleQueryCriteria.fullSelection(T, r))
            .from(r)
            .where(NotificationRuleQueryCriteria.isEnabled(T, r))
            .where(NotificationRuleQueryCriteria.matchesEventType(T, r, eventType))
            .orderByDesc(r.get(T.priority()))
            .orderByAsc(r.get(T.id()))
            .build();
    return dslQueries.query(query, ROW_MAPPER);
  }

  public long count() {
    var r = T.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .selectCount()
            .from(r)
            .build();
    return dslQueries.queryForObject(query, Long.class);
  }

  public boolean delete(long id) {
    return crud.deleteById(id) > 0;
  }

  private static void checkPagination(int offset, int limit) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative, was " + offset);
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive, was " + limit);
    }
  }
}
