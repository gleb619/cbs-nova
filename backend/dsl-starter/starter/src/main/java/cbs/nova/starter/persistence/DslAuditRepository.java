package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslAuditEntity;
import java.sql.Timestamp;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * JDBC access to the append-only {@code dsl_audit} table.
 *
 * <p>
 * Deliberately exposes an insert and a paged query only — the audit log is append-only by
 * convention, so there are no update or delete methods anywhere in the codebase.
 *
 * <p>
 * Follows the {@link JdbcDslRunRepository} idioms: constructor injection via Lombok, named
 * parameters, and an explicit {@link RowMapper}.
 */
@RequiredArgsConstructor
public class DslAuditRepository {

  private static final String COLUMNS =
          "id, occurred_at, actor, action, target, correlation_id, outcome, details_json";

  private static final RowMapper<DslAuditEntity> ROW_MAPPER = (rs, rowNum) -> new DslAuditEntity(
          rs.getLong("id"),
          rs.getTimestamp("occurred_at").toInstant(),
          rs.getString("actor"),
          rs.getString("action"),
          rs.getString("target"),
          rs.getString("correlation_id"),
          rs.getString("outcome"),
          rs.getString("details_json"));

  private final NamedParameterJdbcTemplate jdbcTemplate;

  /**
   * Appends one audit row. {@code id} and {@code occurredAt} on the given entity are used as
   * supplied; callers normally pass a generated identity and {@code Instant.now()}.
   */
  public void insert(DslAuditEntity row) {
    var params = new MapSqlParameterSource()
            .addValue("occurredAt", Timestamp.from(row.occurredAt()))
            .addValue("actor", row.actor())
            .addValue("action", row.action())
            .addValue("target", row.target())
            .addValue("correlationId", row.correlationId())
            .addValue("outcome", row.outcome())
            .addValue("detailsJson", row.detailsJson());
    jdbcTemplate.update("""
            INSERT INTO dsl_audit
                    (occurred_at, actor, action, target, correlation_id, outcome, details_json)
            VALUES (:occurredAt, :actor, :action, :target, :correlationId, :outcome, :detailsJson)
            """, params);
  }

  /**
   * Paged query, newest first. {@code action}, when non-blank, narrows the result set.
   */
  public DslAuditSearchResult search(@Nullable String action, int offset, int limit) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative, was " + offset);
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive, was " + limit);
    }

    String where = "";
    var params = new MapSqlParameterSource();
    if (action != null && !action.isBlank()) {
      where = "WHERE action = :action";
      params.addValue("action", action);
    }

    Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dsl_audit " + where, params, Long.class);

    params.addValue("limit", limit);
    params.addValue("offset", offset);
    List<DslAuditEntity> items = jdbcTemplate.query("""
            SELECT %s FROM dsl_audit %s
            ORDER BY occurred_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """.formatted(COLUMNS, where), params, ROW_MAPPER);
    return new DslAuditSearchResult(items, total != null ? total : 0L);
  }
}
