package cbs.nova.starter.webhook;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.persistence.ExtendedSelectQuery;
import cbs.nova.starter.persistence.ExtendedSelectQueryExecutor;
import cbs.nova.starter.persistence.WebhookDeliveryCrudRepository;
import cbs.nova.starter.persistence.WebhookDeliveryQueryCriteria;
import cbs.nova.starter.persistence.WebhookDeliveryTableColumns;
import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC access to the append-only {@code dsl_webhook_deliveries} table.
 *
 * <p>
 * Deliberately exposes an insert and a paged query only — the delivery log is append-only by
 * convention, so there are no update or delete methods anywhere in the codebase.
 *
 * <p>
 * Follows the {@link cbs.nova.starter.persistence.JdbcDslRunRepository} idioms: squigglesql reads
 * via {@link ExtendedSelectQueryExecutor}, writes delegated to the Spring Data
 * {@link WebhookDeliveryCrudRepository}, and an explicit {@link RowMapper}. Selects are built with
 * squigglesql; the insert flows through the Spring Data CRUD repository.
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

  private final WebhookDeliveryCrudRepository crud;
  private final ExtendedSelectQueryExecutor dslQueries;

  public WebhookDeliveryRecordRepository(WebhookDeliveryCrudRepository crud,
          ExtendedSelectQueryExecutor dslQueries) {
    this.crud = crud;
    this.dslQueries = dslQueries;
  }

  /**
   * Appends one delivery outcome row. The {@code id} and {@code occurredAt} on the given record are
   * used as supplied; callers normally pass a null identity and {@code Instant.now()}.
   */
  public void insert(WebhookDeliveryRecord row) {
    crud.save(new WebhookDeliveryRecord(
            row.id(),
            row.occurredAt(),
            row.subscriptionId(),
            row.eventType(),
            truncate(row.url(), StarterConstants.WEBHOOK_URL_MAX_LENGTH),
            row.status(),
            row.attempts(),
            truncate(row.lastError(), StarterConstants.WEBHOOK_LAST_ERROR_MAX_LENGTH),
            row.durationMs()));
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
