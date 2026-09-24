package cbs.nova.starter.persistence;

import cbs.nova.starter.webhook.WebhookDeliveryRecord;
import org.springframework.data.repository.Repository;

/**
 * Spring Data JDBC access to the append-only {@code dsl_webhook_deliveries} table.
 *
 * <p>
 * Extends the plain {@link Repository} marker instead of {@code CrudRepository}: the delivery log
 * is append-only by convention, so only {@code save} is exposed and no update or delete method is
 * reachable.
 */
public interface WebhookDeliveryCrudRepository extends Repository<WebhookDeliveryRecord, Long> {

  WebhookDeliveryRecord save(WebhookDeliveryRecord entity);
}
