package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslEventEntity;
import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;

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

  private final DslEventCrudRepository crud;
  private final ExtendedSelectQueryExecutor dslQueries;
  private static final DslEventTableColumns T = DslEventTableColumns.of();

  public DslEventRepository(DslEventCrudRepository crud,
          ExtendedSelectQueryExecutor dslQueries) {
    this.crud = Objects.requireNonNull(crud);
    this.dslQueries = Objects.requireNonNull(dslQueries);
  }

  public long insert(DslEventEntity row) {
    Objects.requireNonNull(row, "row");
    return crud.save(row).id();
  }

  /**
   * Returns up to {@code limit} rows for the given aggregate whose {@code id} is strictly greater
   * than {@code sinceId}, ordered by {@code id} ascending. Used by the SSE {@code Last-Event-ID}
   * backfill path: a reconnecting EventSource resends the last id it saw and the controller replays
   * the gap from {@code dsl_events} before the live stream resumes.
   *
   * <p>
   * The combination of {@code (aggregate_type, aggregate_id, id > sinceId)} is supported by the
   * {@code idx_dsl_events_aggregate} index; the {@code ORDER BY id ASC LIMIT N} tail is bounded by
   * the index's natural order on the leading column.
   */
  public List<DslEventEntity> findByAggregateSince(
          String aggregateType, String aggregateId, long sinceId, int limit) {
    if (aggregateType == null || aggregateType.isBlank()) {
      throw new IllegalArgumentException("aggregateType must not be blank");
    }
    if (aggregateId == null || aggregateId.isBlank()) {
      throw new IllegalArgumentException("aggregateId must not be blank");
    }
    if (sinceId < 0) {
      throw new IllegalArgumentException("sinceId must be non-negative, was " + sinceId);
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive, was " + limit);
    }
    var r = T.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .select(DslEventQueryCriteria.fullSelection(T, r))
            .from(r)
            .where(DslEventQueryCriteria.matchesAggregateType(T, r, aggregateType))
            .where(DslEventQueryCriteria.matchesAggregateId(T, r, aggregateId))
            .where(DslEventQueryCriteria.idGreaterThan(T, r, sinceId))
            .orderByAsc(r.get(T.id()))
            .limit(limit)
            .build();
    return dslQueries.query(query, ROW_MAPPER);
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
            .select(DslEventQueryCriteria.fullSelection(T, r))
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
    int updated = crud.markPublished(id);
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
    // fresh builder per query: reusing one across count + data leaks COUNT(*) into the data SELECT
    Supplier<ExtendedSelectQueryExecutor.Builder> filtered = () -> dslQueries.select()
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
              .selectCount()
              .from(r)
              .build();
      total = dslQueries.queryForObject(countQuery, Long.class);
    } else {
      ExtendedSelectQuery countQuery = filtered.get()
              .selectCount()
              .build();
      total = dslQueries.queryForObject(countQuery, Long.class);
    }

    ExtendedSelectQuery dataQuery = filtered.get()
            .select(DslEventQueryCriteria.fullSelection(T, r))
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
