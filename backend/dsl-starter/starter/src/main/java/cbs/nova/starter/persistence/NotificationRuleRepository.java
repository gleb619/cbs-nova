package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.NotificationRuleEntity;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * JDBC access to {@code dsl_notification_rule}. Follows the
 * {@link cbs.nova.starter.persistence.DslEventRepository} idioms: constructor injection via Lombok,
 * named parameters, an explicit {@link RowMapper}, and generated keys surfaced from the insert.
 */
@RequiredArgsConstructor
public class NotificationRuleRepository {

  private static final String COLUMNS = "id, name, enabled, event_type, aggregate_type,"
          + " aggregate_id_pattern, definition_pattern, status, actions, priority, rate_class,"
          + " created_at, updated_at";

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

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public long insert(NotificationRuleEntity row) {
    Objects.requireNonNull(row, "row");
    var params = new MapSqlParameterSource()
            .addValue("name", row.name())
            .addValue("enabled", row.enabled())
            .addValue("eventType", row.eventType())
            .addValue("aggregateType", row.aggregateType())
            .addValue("aggregateIdPattern", row.aggregateIdPattern())
            .addValue("definitionPattern", row.definitionPattern())
            .addValue("status", row.status())
            .addValue("actions", new SqlParameterValue(Types.OTHER, row.actionsJson()))
            .addValue("priority", row.priority())
            .addValue("rateClass", row.rateClass())
            .addValue("createdAt", Timestamp.from(row.createdAt()))
            .addValue("updatedAt", Timestamp.from(row.updatedAt()));
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update("""
            INSERT INTO dsl_notification_rule
                    (name, enabled, event_type, aggregate_type, aggregate_id_pattern,
                     definition_pattern, status, actions, priority, rate_class,
                     created_at, updated_at)
            VALUES (:name, :enabled, :eventType, :aggregateType, :aggregateIdPattern,
                    :definitionPattern, :status, :actions, :priority, :rateClass,
                    :createdAt, :updatedAt)
            """, params, keyHolder, new String[]{"id"});
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException(
              "Insert into dsl_notification_rule returned no generated key");
    }
    return key.longValue();
  }

  public void update(NotificationRuleEntity row) {
    Objects.requireNonNull(row, "row");
    if (row.id() == null) {
      throw new IllegalArgumentException("Cannot update a rule without an id");
    }
    var params = new MapSqlParameterSource()
            .addValue("id", row.id())
            .addValue("name", row.name())
            .addValue("enabled", row.enabled())
            .addValue("eventType", row.eventType())
            .addValue("aggregateType", row.aggregateType())
            .addValue("aggregateIdPattern", row.aggregateIdPattern())
            .addValue("definitionPattern", row.definitionPattern())
            .addValue("status", row.status())
            .addValue("actions", new SqlParameterValue(Types.OTHER, row.actionsJson()))
            .addValue("priority", row.priority())
            .addValue("rateClass", row.rateClass())
            .addValue("updatedAt", Timestamp.from(row.updatedAt()));
    jdbcTemplate.update("""
            UPDATE dsl_notification_rule
            SET name = :name, enabled = :enabled, event_type = :eventType,
                aggregate_type = :aggregateType, aggregate_id_pattern = :aggregateIdPattern,
                definition_pattern = :definitionPattern, status = :status,
                actions = :actions, priority = :priority, rate_class = :rateClass,
                updated_at = :updatedAt
            WHERE id = :id
            """, params);
  }

  public Optional<NotificationRuleEntity> findById(long id) {
    List<NotificationRuleEntity> items = jdbcTemplate.query(
            "SELECT %s FROM dsl_notification_rule WHERE id = :id".formatted(COLUMNS),
            new MapSqlParameterSource("id", id), ROW_MAPPER);
    return items.stream().findFirst();
  }

  public NotificationRuleSearchResult findAll(int offset, int limit) {
    checkPagination(offset, limit);
    var params = new MapSqlParameterSource()
            .addValue("limit", limit)
            .addValue("offset", offset);
    Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dsl_notification_rule", params,
            Long.class);
    List<NotificationRuleEntity> items = jdbcTemplate.query("""
            SELECT %s FROM dsl_notification_rule
            ORDER BY priority DESC, id ASC
            LIMIT :limit OFFSET :offset
            """.formatted(COLUMNS), params, ROW_MAPPER);
    return new NotificationRuleSearchResult(items, total != null ? total : 0L);
  }

  /**
   * The engine's hot lookup: all enabled rules for an exact event type, in match order (priority
   * desc, id asc). Finer filter dimensions are matched in memory by the engine.
   */
  public List<NotificationRuleEntity> findMatchingEnabled(String eventType) {
    return jdbcTemplate.query("""
            SELECT %s FROM dsl_notification_rule
            WHERE enabled = TRUE AND event_type = :eventType
            ORDER BY priority DESC, id ASC
            """.formatted(COLUMNS), new MapSqlParameterSource("eventType", eventType),
            ROW_MAPPER);
  }

  public long count() {
    Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dsl_notification_rule",
            new MapSqlParameterSource(), Long.class);
    return total != null ? total : 0L;
  }

  public boolean delete(long id) {
    int deleted = jdbcTemplate.update("DELETE FROM dsl_notification_rule WHERE id = :id",
            new MapSqlParameterSource("id", id));
    return deleted > 0;
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
