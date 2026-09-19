package cbs.nova.starter.notification;

import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;

/**
 * Delivery target for a notification rule action. The engine wraps {@link #deliver} in a try/catch
 * so implementations can never break the publish path, but a sink that converts its own errors into
 * a {@link FiringOutcome#failure} produces better audit detail.
 */
public interface NotificationSink {

  /** Sink type as it appears in the rule JSON {@code actions[].sink} field. */
  String sinkType();

  FiringOutcome deliver(NotificationRuleEntity rule, NotificationActionDto action,
          DomainEvent event);
}
