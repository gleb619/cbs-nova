package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslEventEntity;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

public class DslEventRepository {

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
  private final ExtendedSelectQueryExecutor dslQueries;
  private static final DslEventTableColumns T = DslEventTableColumns.of();

  public DslEventRepository(NamedParameterJdbcTemplate jdbcTemplate,
          ExtendedSelectQueryExecutor dslQueries) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    this.dslQueries = Objects.requireNonNull(dslQueries);
  }

  public long insert(DslEventEntity row) {
    Objects.requireNonNull(row, "row");
    var params = new MapSqlParameterSource()
            .addValue("eventType", row.eventType())
            .addValue("aggregateType", row.aggregateType())
            .addValue("aggregateId", row.aggregateId())
            .addValue("correlationId", row.correlationId())
            .addValue("payload", new SqlParameterValue(Types.OTHER, row.payloadJson()))
            .addValue("schemaVersion", row.schemaVersion())
            .addValue("createdAt", Timestamp.from(row.createdAt()));
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update("""
            INSERT INTO dsl_events
                    (event_type, aggregate_type, aggregate_id, correlation_id,
                     payload, schema_version, created_at)
            VALUES (:eventType, :aggregateType, :aggregateId, :correlationId,
                    :payload, :schemaVersion, :createdAt)
            """, params, keyHolder, new String[]{"id"});
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("Insert into dsl_events returned no generated key");
    }
    return key.longValue();
  }

  /**
   * Returns up to {@code limit} rows that have not yet been successfully published to the MQ sink.
   */
  public List<DslEventEntity> findUnpublished(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive, was " + limit);
    }
    var r = T.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .select(DslEventQueryCriteria.fullSelection(T, r)
                    .toArray(new com.github.squigglesql.squigglesql.Selectable[0]))
            .from(r)
            .where(DslEventQueryCriteria.isUnpublished(T, r))
            .orderByAsc(r.get(T.id()))
            .limit(limit)
            .build();
    return dslQueries.query(query, ROW_MAPPER);
  }

  /**
   * Marks the row as successfully published to the MQ sink.
   */
  public void markPublished(long id) {
    var params = new MapSqlParameterSource().addValue("id", id);
    int updated = jdbcTemplate.update(
            "UPDATE dsl_events SET mq_published = true WHERE id = :id", params);
    if (updated == 0) {
      throw new IllegalStateException(
              "Marking dsl_events row published had no effect for id " + id);
    }
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

    var r = T.refer();
    var builder = dslQueries.select()
            .from(r)
            .whereIf(eventType != null && !eventType.isBlank(),
                    () -> DslEventQueryCriteria.matchesEventType(T, r, eventType))
            .whereIf(aggregateType != null && !aggregateType.isBlank(),
                    () -> DslEventQueryCriteria.matchesAggregateType(T, r, aggregateType))
            .whereIf(aggregateId != null && !aggregateId.isBlank(),
                    () -> DslEventQueryCriteria.matchesAggregateId(T, r, aggregateId))
            .whereIf(correlationId != null && !correlationId.isBlank(),
                    () -> DslEventQueryCriteria.matchesCorrelationId(T, r, correlationId))
            .whereIf(since != null,
                    () -> DslEventQueryCriteria.occurredSince(T, r, since));

    Long total;
    if (eventType == null && aggregateType == null && aggregateId == null
            && (correlationId == null || correlationId.isBlank()) && since == null) {
      ExtendedSelectQuery countQuery = dslQueries.select()
              .select(com.github.squigglesql.squigglesql.literal.Literal.unsafe("COUNT(*)"))
              .from(r)
              .build();
      total = dslQueries.queryForObject(countQuery, Long.class);
    } else {
      ExtendedSelectQuery countQuery = builder
              .select(com.github.squigglesql.squigglesql.literal.Literal.unsafe("COUNT(*)"))
              .build();
      total = dslQueries.queryForObject(countQuery, Long.class);
    }

    ExtendedSelectQuery dataQuery = builder
            .select(DslEventQueryCriteria.fullSelection(T, r)
                    .toArray(new com.github.squigglesql.squigglesql.Selectable[0]))
            .from(r)
            .orderByDesc(r.get(T.createdAt()))
            .orderByDesc(r.get(T.id()))
            .limit(limit)
            .offset(offset)
            .build();
    List<DslEventEntity> items = dslQueries.query(dataQuery, ROW_MAPPER);
    return new DslEventSearchResult(items, total != null ? total : 0L);
  }
}
