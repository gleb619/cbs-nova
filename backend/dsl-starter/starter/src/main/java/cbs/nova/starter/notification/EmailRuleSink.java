package cbs.nova.starter.notification;

import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code email} sink — NOOP placeholder. There is no mail transport in the starter yet; the sink
 * logs the would-be delivery and records a {@code noop} firing so rules can be configured and
 * tested ahead of a real adapter.
 */
@Slf4j
public class EmailRuleSink implements NotificationSink {

  @Override
  public String sinkType() {
    return "email";
  }

  @Override
  public FiringOutcome deliver(NotificationRuleEntity rule, NotificationActionDto action,
          DomainEvent event) {
    log.info("[DSL notifications] email sink is a placeholder: rule '{}' would notify '{}' for"
            + " event {} on {}/{}", rule.name(), action.to(), event.eventType(),
            event.aggregateType(), event.aggregateId());
    return FiringOutcome.noop("email sink is a placeholder", 0L);
  }
}
