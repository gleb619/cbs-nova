package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslEventEntity;
import java.time.Instant;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

/**
 * Spring Data JDBC access to the append-only {@code dsl_events} table (T411).
 *
 * <p>
 * Extends the plain {@link Repository} marker instead of {@code CrudRepository}: the event store is
 * append-only by convention. Only {@code save} (insert) plus the transactional-outbox transition
 * {@link #markPublished} (the one update tolerated by the append-only invariant) are exposed.
 */
public interface DslEventCrudRepository extends Repository<DslEventEntity, Long> {

  DslEventEntity save(DslEventEntity entity);

  @Modifying
  @Query("UPDATE dsl_events SET mq_published = true WHERE id = :id")
  int markPublished(long id);

  /**
   * Maintenance-only: deletes at most {@code batchSize} rows whose {@code created_at} is strictly
   * older than {@code cutoff}. Returns the number of rows actually deleted.
   *
   * <p>
   * Backed by the {@code idx_dsl_events_created_at} index (V1__init.sql) so the cutoff scan is
   * bounded. The {@code LIMIT} clause sits inside a {@code WHERE IN (subquery)} because PostgreSQL
   * does not support {@code LIMIT} directly in {@code DELETE}; this shape is portable across
   * Postgres and the supported H2 modes. The caller is expected to loop on this method until it
   * returns less than {@code batchSize}.
   */
  @Modifying
  @Query("""
          DELETE FROM dsl_events
          WHERE id IN (
              SELECT id FROM dsl_events
              WHERE created_at < :cutoff
              LIMIT :batchSize
          )
          """)
  int deleteOlderThan(Instant cutoff, int batchSize);
}
