package cbs.nova.starter.webhook;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.persistence.ExtendedSelectQuery;
import cbs.nova.starter.persistence.ExtendedSelectQueryExecutor;
import cbs.nova.starter.persistence.WebhookDeliveryQueryCriteria;
import cbs.nova.starter.persistence.WebhookDeliveryTableColumns;
import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.sql.Timestamp;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * JDBC access to the append-only {@code dsl_webhook_deliveries} table.
 *
 * <p>
 * Deliberately exposes an insert and a paged query only — the delivery log is append-only by
 * convention, so there are no update or delete methods anywhere in the codebase.
 *
 * <p>
 * Follows the {@link cbs.nova.starter.persistence.JdbcDslRunRepository} idioms: explicit
 * constructor injection, named parameters, and an explicit {@link RowMapper}.
 */
public class WebhookDeliveryRecordRepository {

  private static final RowMapper<WebhookDeliveryRecord> ROW_MAPPER = (rs,
          rowNum) -> new WebhookDeliveryRecord(
                  rs.getLong("id"),
                  rs.getTimestamp("occurred_at").toInstant(),
                  rs.getString("subscription_id"),
                  rs.getString("event_type"),
                  rs.getString("url"),
                  rs.getString("status"),
                  rs.getInt("attempts"),
                  rs.getString("last_error"),
                  rs.getObject("duration_ms", Long.class));

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ExtendedSelectQueryExecutor dslQueries;

  public WebhookDeliveryRecordRepository(NamedParameterJdbcTemplate jdbcTemplate,
          ExtendedSelectQueryExecutor dslQueries) {
    this.jdbcTemplate = jdbcTemplate;
    this.dslQueries = dslQueries;
  }

  /**
   * Appends one delivery outcome row. The {@code id} and {@code occurredAt} on the given record are
   * used as supplied; callers normally pass a null identity and {@code Instant.now()}.
   */
  public void insert(WebhookDeliveryRecord row) {
    var params = new MapSqlParameterSource()
            .addValue("occurredAt", Timestamp.from(row.occurredAt()))
            .addValue("subscriptionId", row.subscriptionId())
            .addValue("eventType", row.eventType())
            .addValue("url", truncate(row.url(), StarterConstants.WEBHOOK_URL_MAX_LENGTH))
            .addValue("status", row.status())
            .addValue("attempts", row.attempts())
            .addValue("lastError", truncate(row.lastError(),
                    StarterConstants.WEBHOOK_LAST_ERROR_MAX_LENGTH))
            .addValue("durationMs", row.durationMs());
    jdbcTemplate.update(
            """
                    INSERT INTO dsl_webhook_deliveries
                            (occurred_at, subscription_id, event_type, url, status, attempts, last_error, duration_ms)
                    VALUES (:occurredAt, :subscriptionId, :eventType, :url, :status, :attempts, :lastError, :durationMs)
                    """,
            params);
  }

  /**
   * Paged query, newest first. {@code subscriptionId}, when non-blank, narrows the result set.
   */
  public WebhookDeliverySearchResult search(@Nullable String subscriptionId, int offset,
          int limit) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative, was " + offset);
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive, was " + limit);
    }

    WebhookDeliveryTableColumns t = WebhookDeliveryTableColumns.of();
    TableReference r = t.refer();

    long total;
    if (subscriptionId == null || subscriptionId.isBlank()) {
      ExtendedSelectQuery countQuery = dslQueries.select()
              .from(r)
              .select(Literal.unsafe("COUNT(*)"))
              .build();
      total = dslQueries.queryForObject(countQuery, Long.class);
    } else {
      ExtendedSelectQuery countQuery = dslQueries.select()
              .from(r)
              .select(Literal.unsafe("COUNT(*)"))
              .where(WebhookDeliveryQueryCriteria.matchesSubscriptionId(t, r, subscriptionId))
              .build();
      total = dslQueries.queryForObject(countQuery, Long.class);
    }

    ExtendedSelectQuery dataQuery = dslQueries.select()
            .from(r)
            .select(WebhookDeliveryQueryCriteria.fullSelection(t, r).toArray(Selectable[]::new))
            .whereIf(subscriptionId != null && !subscriptionId.isBlank(),
                    () -> WebhookDeliveryQueryCriteria.matchesSubscriptionId(t, r, subscriptionId))
            .orderByDesc(r.get(t.occurredAt()))
            .orderByDesc(r.get(t.id()))
            .limit(limit)
            .offset(offset)
            .build();
    List<WebhookDeliveryRecord> items = dslQueries.query(dataQuery, ROW_MAPPER);
    return new WebhookDeliverySearchResult(items, total);
  }

  private static @Nullable String truncate(@Nullable String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
