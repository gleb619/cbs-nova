package cbs.nova.starter.notification;

import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import cbs.nova.starter.security.WebhookUrlGuard;
import cbs.nova.starter.webhook.WebhookProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Webhook-shaped sink for chat/ops adapters ({@code slack}, {@code pagerduty}): POSTs a small
 * {@code {"text": ...}} JSON body to the action URL. Single attempt, no retry, no signing —
 * deliberately simpler than the {@code webhook} sink. HTTP-level errors are converted into a
 * {@link FiringOutcome#failure} rather than thrown.
 *
 * <p>
 * T811: the URL is re-checked at dispatch time with the shared {@link WebhookUrlGuard}; an unsafe
 * URL becomes a {@link FiringOutcome#failure} and never reaches the HTTP client.
 */
@Slf4j
@RequiredArgsConstructor
public class GenericJsonRuleSink implements NotificationSink {

  private final String sinkType;

  private final HttpClient httpClient;

  private final ObjectMapper objectMapper;

  private final Duration timeout;

  private final WebhookProperties webhookProperties;

  @Override
  public String sinkType() {
    return sinkType;
  }

  @Override
  public FiringOutcome deliver(NotificationRuleEntity rule, NotificationActionDto action,
          DomainEvent event) {
    Instant started = Instant.now();
    String reason = WebhookUrlGuard.sinkRejectionReason(action.url(), webhookProperties);
    if (reason != null) {
      log.warn("Rejecting {} sink delivery: {}", sinkType, reason);
      return FiringOutcome.failure(reason, 0L);
    }
    try {
      byte[] body = objectMapper.writeValueAsBytes(Map.of(
              "text", "Rule '" + rule.name() + "': " + event.eventType() + " on "
                      + event.aggregateType() + "/" + event.aggregateId()));
      HttpRequest request = HttpRequest.newBuilder(URI.create(action.url()))
              .header("Content-Type", "application/json")
              .timeout(timeout)
              .POST(HttpRequest.BodyPublishers.ofByteArray(body))
              .build();
      HttpResponse<Void> response = httpClient.send(request,
              HttpResponse.BodyHandlers.discarding());
      long durationMs = Duration.between(started, Instant.now()).toMillis();
      if (response.statusCode() >= 200 && response.statusCode() < 400) {
        return FiringOutcome.success("http " + response.statusCode(), durationMs);
      }
      return FiringOutcome.failure("http " + response.statusCode(), durationMs);
    } catch (Exception ex) {
      return FiringOutcome.failure(ex.getMessage(),
              Duration.between(started, Instant.now()).toMillis());
    }
  }
}
