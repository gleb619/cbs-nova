package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.NotificationRuleFiringEntity;
import java.time.Instant;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

/**
 * Spring Data JDBC access to the append-only {@code dsl_notification_rule_firing} audit table.
 *
 * <p>
 * Extends the plain {@link Repository} marker instead of {@code CrudRepository}: the firing log is
 * append-only by convention, so only {@code save} is exposed.
 *
 * <p>
 * <b>Maintenance-only delete:</b> {@link #deleteOlderThan(Instant, int)} is the single exception to
 * the append-only invariant and exists solely so the opt-in
 * {@code dsl.maintenance.notification-firing-retention} purge (T814) can age rows out of the table.
 * It is intentionally narrow — fixed SQL, no cascading effects — and is not exposed from any REST
 * handler. See {@link NotificationRuleFiringRepository#purgeOlderThan}.
 */
public interface NotificationRuleFiringCrudRepository
        extends
          Repository<NotificationRuleFiringEntity, Long> {

  NotificationRuleFiringEntity save(NotificationRuleFiringEntity row);

  /**
   * Maintenance-only: deletes at most {@code batchSize} rows whose {@code created_at} is strictly
   * older than {@code cutoff}. Returns the number of rows actually deleted.
   *
   * <p>
   * Backed by the {@code idx_dsl_notification_rule_firing_created_at} index (V1__init.sql) so the
   * cutoff scan is bounded. The {@code LIMIT} clause sits inside a {@code WHERE IN (subquery)}
   * because PostgreSQL does not support {@code LIMIT} directly in {@code DELETE}; this shape is
   * portable across Postgres and the supported H2 modes. The caller is expected to loop on this
   * method until it returns less than {@code batchSize}.
   */
  @Modifying
  @Query("""
          DELETE FROM dsl_notification_rule_firing
          WHERE id IN (
              SELECT id FROM dsl_notification_rule_firing
              WHERE created_at < :cutoff
              LIMIT :batchSize
          )
          """)
  int deleteOlderThan(Instant cutoff, int batchSize);
}
