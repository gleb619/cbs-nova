package cbs.nova.starter.notification;

import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import cbs.nova.starter.webhook.WebhookDeliveryResult;
import cbs.nova.starter.webhook.WebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code webhook} sink. Reuses {@link WebhookDispatcher#dispatchTo} so rule deliveries share the
 * static-subscription transport (HMAC signing, https enforcement, bounded retries) without writing
 * into the {@code dsl_webhook_deliveries} log — rule deliveries are audited in
 * {@code dsl_notification_rule_firing} instead.
 */
@Slf4j
@RequiredArgsConstructor
public class WebhookRuleSink implements NotificationSink {

  private final WebhookDispatcher dispatcher;

  @Override
  public String sinkType() {
    return "webhook";
  }

  @Override
  public FiringOutcome deliver(NotificationRuleEntity rule, NotificationActionDto action,
          DomainEvent event) {
    WebhookDeliveryResult result = dispatcher.dispatchTo(action.url(), action.secret(),
            event.eventType(), event);
    if ("rejected".equals(result.status())) {
      return FiringOutcome.rejected(result.error(), result.durationMs());
    }
    if (result.isSuccess()) {
      return FiringOutcome.success("http " + result.status(), result.durationMs());
    }
    String detail = result.error() != null ? result.error() : "http " + result.status();
    return FiringOutcome.failure(detail, result.durationMs());
  }
}
