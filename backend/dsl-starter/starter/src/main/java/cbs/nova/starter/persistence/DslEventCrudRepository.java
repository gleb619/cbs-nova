package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslEventEntity;
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
}
