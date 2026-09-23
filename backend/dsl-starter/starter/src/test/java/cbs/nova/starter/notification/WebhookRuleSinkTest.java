package cbs.nova.starter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import cbs.nova.starter.webhook.WebhookDeliveryResult;
import cbs.nova.starter.webhook.WebhookDispatcher;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit specs for {@link WebhookRuleSink}. The sink delegates the actual HTTP delivery to
 * {@link WebhookDispatcher#dispatchTo} and maps the {@link WebhookDeliveryResult} onto a
 * {@link FiringOutcome}: {@code rejected} status → {@code rejected}, 2xx/3xx → {@code success} with
 * {@code "http <status>"} detail, anything else → {@code failure}. Transport exceptions are NOT
 * caught here — the engine wraps {@code deliver} in its own try/catch.
 */
class WebhookRuleSinkTest {

  private final WebhookDispatcher dispatcher = mock(WebhookDispatcher.class);

  private final WebhookRuleSink sink = new WebhookRuleSink(dispatcher);

  @Test
  void sinkTypeIsWebhook() {
    assertThat(sink.sinkType()).isEqualTo("webhook");
  }

  @Test
  void successfulDeliveryForwardsUrlSecretEventTypeAndEvent() {
    NotificationRuleEntity rule = rule("loan-failures");
    NotificationActionDto action = new NotificationActionDto("webhook",
            "https://hooks.example.com/loan", "topsecret", null);
    DomainEvent.RunFailed event = runFailed();
    when(dispatcher.dispatchTo(eq(action.url()), eq(action.secret()), eq(event.eventType()),
            eq(event)))
            .thenReturn(new WebhookDeliveryResult("200", 1, null, 42L));

    FiringOutcome outcome = sink.deliver(rule, action, event);

    verify(dispatcher).dispatchTo(action.url(), action.secret(), event.eventType(), event);
    assertThat(outcome.outcome()).isEqualTo("success");
    assertThat(outcome.detail()).isEqualTo("http 200");
    assertThat(outcome.durationMs()).isEqualTo(42L);
  }

  @Test
  void redirectStatusIsAlsoSuccess() {
    NotificationActionDto action = new NotificationActionDto("webhook",
            "https://hooks.example.com/loan", null, null);
    when(dispatcher.dispatchTo(any(), any(), any(), any()))
            .thenReturn(new WebhookDeliveryResult("302", 1, null, 7L));

    FiringOutcome outcome = sink.deliver(rule("r"), action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("success");
    assertThat(outcome.detail()).isEqualTo("http 302");
  }

  @Test
  void non2xxStatusWithoutErrorBecomesFailureWithHttpDetail() {
    NotificationActionDto action = new NotificationActionDto("webhook",
            "https://hooks.example.com/loan", null, null);
    when(dispatcher.dispatchTo(any(), any(), any(), any()))
            .thenReturn(new WebhookDeliveryResult("500", 3, null, 99L));

    FiringOutcome outcome = sink.deliver(rule("r"), action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("failure");
    assertThat(outcome.detail()).isEqualTo("http 500");
    assertThat(outcome.durationMs()).isEqualTo(99L);
  }

  @Test
  void non2xxStatusWithErrorPrefersErrorDetail() {
    NotificationActionDto action = new NotificationActionDto("webhook",
            "https://hooks.example.com/loan", null, null);
    when(dispatcher.dispatchTo(any(), any(), any(), any()))
            .thenReturn(new WebhookDeliveryResult("503", 3, "server overloaded", 12L));

    FiringOutcome outcome = sink.deliver(rule("r"), action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("failure");
    assertThat(outcome.detail()).isEqualTo("server overloaded");
  }

  @Test
  void rejectedStatusBecomesRejectedOutcome() {
    NotificationActionDto action = new NotificationActionDto("webhook",
            "http://plain-http-not-allowed.example.com/hook", null, null);
    when(dispatcher.dispatchTo(any(), any(), any(), any()))
            .thenReturn(new WebhookDeliveryResult("rejected", 0, "plain http url not allowed",
                    0L));

    FiringOutcome outcome = sink.deliver(rule("r"), action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("rejected");
    assertThat(outcome.detail()).isEqualTo("plain http url not allowed");
    assertThat(outcome.durationMs()).isZero();
  }

  @Test
  void dispatcherExceptionsPropagateUncaught() {
    NotificationActionDto action = new NotificationActionDto("webhook",
            "https://hooks.example.com/loan", null, null);
    when(dispatcher.dispatchTo(any(), any(), any(), any()))
            .thenThrow(new RuntimeException("dispatcher boom"));

    assertThatThrownBy(() -> sink.deliver(rule("r"), action, runFailed()))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("dispatcher boom");
  }

  private static NotificationRuleEntity rule(String name) {
    Instant now = Instant.now();
    return new NotificationRuleEntity(1L, name, true, "RunFailed", null, null, null, null, "[]", 0,
            "default", now, now);
  }

  private static DomainEvent.RunFailed runFailed() {
    Instant now = Instant.now();
    return new DomainEvent.RunFailed("run-1", "LoanOrigination", DslRunStatus.FAILED, "boom", now,
            now, null, null);
  }
}
