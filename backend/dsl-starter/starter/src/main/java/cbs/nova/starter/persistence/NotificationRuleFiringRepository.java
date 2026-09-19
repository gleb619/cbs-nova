package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.NotificationRuleFiringEntity;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * JDBC access to the append-only {@code dsl_notification_rule_firing} audit table: an insert plus a
 * paged, filterable read. There is intentionally no update or delete path.
 */
@RequiredArgsConstructor
public class NotificationRuleFiringRepository {

  private static final String COLUMNS = "id, event_id, rule_id, rule_name, sink, outcome, detail,"
          + " duration_ms, created_at";

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

  private final NamedParameterJdbcTemplate jdbcTemplate;

  /** Appends one firing row and returns the generated id. */
  public long insert(NotificationRuleFiringEntity row) {
    Objects.requireNonNull(row, "row");
    var params = new MapSqlParameterSource()
            .addValue("eventId", row.eventId())
            .addValue("ruleId", row.ruleId())
            .addValue("ruleName", row.ruleName())
            .addValue("sink", row.sink())
            .addValue("outcome", row.outcome())
            .addValue("detail", row.detail())
            .addValue("durationMs", row.durationMs())
            .addValue("createdAt", Timestamp.from(row.createdAt()));
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update("""
            INSERT INTO dsl_notification_rule_firing
                    (event_id, rule_id, rule_name, sink, outcome, detail, duration_ms, created_at)
            VALUES (:eventId, :ruleId, :ruleName, :sink, :outcome, :detail, :durationMs, :createdAt)
            """, params, keyHolder, new String[]{"id"});
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException(
              "Insert into dsl_notification_rule_firing returned no generated key");
    }
    return key.longValue();
  }

  /** Paged query, newest first. {@code ruleId}, when non-null, narrows the result set. */
  public NotificationRuleFiringSearchResult search(@Nullable Long ruleId, int offset, int limit) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative, was " + offset);
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive, was " + limit);
    }

    String where = "";
    var params = new MapSqlParameterSource();
    if (ruleId != null) {
      where = "WHERE rule_id = :ruleId";
      params.addValue("ruleId", ruleId);
    }

    Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dsl_notification_rule_firing " + where, params, Long.class);

    params.addValue("limit", limit);
    params.addValue("offset", offset);
    List<NotificationRuleFiringEntity> items = jdbcTemplate.query("""
            SELECT %s FROM dsl_notification_rule_firing %s
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """.formatted(COLUMNS, where), params, ROW_MAPPER);
    return new NotificationRuleFiringSearchResult(items, total != null ? total : 0L);
  }
}
