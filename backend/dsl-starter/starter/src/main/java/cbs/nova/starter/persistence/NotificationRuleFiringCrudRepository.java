package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.NotificationRuleFiringEntity;
import org.springframework.data.repository.Repository;

/**
 * Spring Data JDBC access to the append-only {@code dsl_notification_rule_firing} audit table.
 *
 * <p>
 * Extends the plain {@link Repository} marker instead of {@code CrudRepository}: the firing log is
 * append-only by convention, so only {@code save} is exposed.
 */
public interface NotificationRuleFiringCrudRepository
        extends
          Repository<NotificationRuleFiringEntity, Long> {

  NotificationRuleFiringEntity save(NotificationRuleFiringEntity entity);
}
