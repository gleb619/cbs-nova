package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslEventEntity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@RequiredArgsConstructor
public class DslEventRepository {

  private static final String COLUMNS = "id, event_type, aggregate_type, aggregate_id, correlation_id, payload, schema_version, created_at";

  private static final RowMapper<DslEventEntity> ROW_MAPPER = (rs, rowNum) -> new DslEventEntity(
          rs.getLong("id"),
          rs.getString("event_type"),
          rs.getString("aggregate_type"),
          rs.getString("aggregate_id"),
          rs.getString("correlation_id"),
          rs.getString("payload"),
          rs.getInt("schema_version"),
          rs.getTimestamp("created_at").toInstant());

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public void insert(DslEventEntity row) {
    Objects.requireNonNull(row, "row");
    var params = new MapSqlParameterSource()
            .addValue("eventType", row.eventType())
            .addValue("aggregateType", row.aggregateType())
            .addValue("aggregateId", row.aggregateId())
            .addValue("correlationId", row.correlationId())
            .addValue("payload", row.payloadJson())
            .addValue("schemaVersion", row.schemaVersion())
            .addValue("createdAt", Timestamp.from(row.createdAt()));
    jdbcTemplate.update("""
            INSERT INTO dsl_events
                    (event_type, aggregate_type, aggregate_id, correlation_id,
                     payload, schema_version, created_at)
            VALUES (:eventType, :aggregateType, :aggregateId, :correlationId,
                    :payload, :schemaVersion, :createdAt)
            """, params);
  }

  public DslEventSearchResult search(
          @Nullable String eventType,
          @Nullable String aggregateType,
          @Nullable String aggregateId,
          @Nullable String correlationId,
          @Nullable Instant since,
          int offset,
          int limit) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative, was " + offset);
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive, was " + limit);
    }

    StringBuilder where = new StringBuilder();
    var params = new MapSqlParameterSource();
    List<String> clauses = new ArrayList<>();
    if (eventType != null && !eventType.isBlank()) {
      clauses.add("event_type = :eventType");
      params.addValue("eventType", eventType);
    }
    if (aggregateType != null && !aggregateType.isBlank()) {
      clauses.add("aggregate_type = :aggregateType");
      params.addValue("aggregateType", aggregateType);
    }
    if (aggregateId != null && !aggregateId.isBlank()) {
      clauses.add("aggregate_id = :aggregateId");
      params.addValue("aggregateId", aggregateId);
    }
    if (correlationId != null && !correlationId.isBlank()) {
      clauses.add("correlation_id = :correlationId");
      params.addValue("correlationId", correlationId);
    }
    if (since != null) {
      clauses.add("created_at >= :since");
      params.addValue("since", Timestamp.from(since));
    }
    if (!clauses.isEmpty()) {
      where.append("WHERE ").append(String.join(" AND ", clauses));
    }

    Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dsl_events " + where, params, Long.class);

    params.addValue("limit", limit);
    params.addValue("offset", offset);
    List<DslEventEntity> items = jdbcTemplate.query("""
            SELECT %s FROM dsl_events %s
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """.formatted(COLUMNS, where), params, ROW_MAPPER);
    return new DslEventSearchResult(items, total != null ? total : 0L);
  }
}
