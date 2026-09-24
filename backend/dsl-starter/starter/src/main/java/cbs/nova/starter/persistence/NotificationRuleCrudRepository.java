package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.NotificationRuleEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * Spring Data JDBC repository for {@code dsl_notification_rule}. Writes flow through this CRUD
 * interface: {@code save} for insert/update, {@code deleteById} returning affected-row count, and
 * {@code update} returning affected-row count.
 */
public interface NotificationRuleCrudRepository
        extends
          CrudRepository<NotificationRuleEntity, Long> {

  @Modifying
  @Query("""
          UPDATE dsl_notification_rule
          SET name = :name, enabled = :enabled, event_type = :eventType,
              aggregate_type = :aggregateType, aggregate_id_pattern = :aggregateIdPattern,
              definition_pattern = :definitionPattern, status = :status,
              actions = :actions, priority = :priority, rate_class = :rateClass,
              updated_at = :updatedAt
          WHERE id = :id
          """)
  int update(
          long id,
          String name,
          boolean enabled,
          String eventType,
          String aggregateType,
          String aggregateIdPattern,
          String definitionPattern,
          String status,
          String actions,
          int priority,
          String rateClass,
          java.time.Instant updatedAt);

  @Modifying
  @Query("DELETE FROM dsl_notification_rule WHERE id = :id")
  int deleteById(long id);
}
