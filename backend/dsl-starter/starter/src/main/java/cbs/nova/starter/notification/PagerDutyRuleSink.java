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
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

/**
 * PagerDuty Events API v2 sink ({@code https://events.pagerduty.com/v2/enqueue}). POSTs a real v2
 * envelope ({@code routing_key}, {@code event_action}, {@code dedup_key}, {@code payload{summary,
 * source, severity, custom_details}}) — the generic {@code {"text": ...}} shape used by the
 * Slack/chat adapter is rejected (HTTP 400) by the real PagerDuty endpoint.
 *
 * <p>
 * Routing key is read from {@link NotificationActionDto#secret()} — the same field the Slack-shaped
 * generic sink ignored. When {@code action.url} is blank, the well-known Events API v2 endpoint is
 * used as the default. URL safety is re-checked at dispatch time with the shared
 * {@link WebhookUrlGuard}, including for the default URL, so an unsafe custom URL never reaches the
 * HTTP client.
 *
 * <p>
 * Severity is {@code error} for any event whose {@code eventType} ends in {@code Failed} (e.g.
 * {@code RunFailed}, {@code ReloadFailed}) and {@code info} for everything else. HTTP 202 is the
 * PagerDuty success status; non-2xx becomes {@link FiringOutcome#failure} whose {@code detail} is
 * the upstream status only — the response body and the routing key never land in the failure row.
 */
@Slf4j
@RequiredArgsConstructor
public class PagerDutyRuleSink implements NotificationSink {

  static final String SINK_TYPE = "pagerduty";

  static final String DEFAULT_URL = "https://events.pagerduty.com/v2/enqueue";

  static final String DEFAULT_SOURCE = "cbs-nova";

  static final String SEVERITY_ERROR = "error";

  static final String SEVERITY_INFO = "info";

  private final HttpClient httpClient;

  private final ObjectMapper objectMapper;

  private final Duration timeout;

  private final WebhookProperties webhookProperties;

  @Override
  public String sinkType() {
    return SINK_TYPE;
  }

  @Override
  public FiringOutcome deliver(NotificationRuleEntity rule, NotificationActionDto action,
          DomainEvent event) {
    if (action.secret() == null || action.secret().isBlank()) {
      return FiringOutcome.failure("pagerduty requires routing key (secret)", 0L);
    }
    String url = (action.url() == null || action.url().isBlank()) ? DEFAULT_URL : action.url();
    String reason = WebhookUrlGuard.sinkRejectionReason(url, webhookProperties);
    if (reason != null) {
      log.warn("Rejecting {} sink delivery: {}", SINK_TYPE, reason);
      return FiringOutcome.failure(reason, 0L);
    }
    Instant started = Instant.now();
    try {
      byte[] payload = objectMapper.writeValueAsBytes(buildBody(rule, action, event));
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
              .header("Content-Type", "application/json")
              .timeout(timeout)
              .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
              .build();
      HttpResponse<Void> response = httpClient.send(request,
              HttpResponse.BodyHandlers.discarding());
      long durationMs = Duration.between(started, Instant.now()).toMillis();
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return FiringOutcome.success("http " + response.statusCode(), durationMs);
      }
      return FiringOutcome.failure("http " + response.statusCode(), durationMs);
    } catch (Exception ex) {
      return FiringOutcome.failure(ex.getMessage(),
              Duration.between(started, Instant.now()).toMillis());
    }
  }

  private static Map<String, Object> buildBody(NotificationRuleEntity rule,
          NotificationActionDto action, DomainEvent event) {
    Map<String, Object> customDetails = new LinkedHashMap<>();
    customDetails.put("eventType", event.eventType());
    customDetails.put("aggregateType", event.aggregateType());
    customDetails.put("aggregateId", event.aggregateId());
    customDetails.put("correlationId", event.correlationId());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("summary", "Rule '" + rule.name() + "': " + event.eventType() + " on "
            + event.aggregateType() + "/" + event.aggregateId());
    payload.put("source", DEFAULT_SOURCE);
    payload.put("severity", severity(event.eventType()));
    payload.put("custom_details", customDetails);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("routing_key", action.secret());
    body.put("event_action", "trigger");
    body.put("dedup_key", dedupKey(rule.id(), event));
    body.put("payload", payload);
    return body;
  }

  static String severity(@Nullable String eventType) {
    if (eventType != null && eventType.endsWith("Failed")) {
      return SEVERITY_ERROR;
    }
    return SEVERITY_INFO;
  }

  static String dedupKey(long ruleId, DomainEvent event) {
    return ruleId + ":" + event.aggregateType() + ":" + event.aggregateId();
  }
}
