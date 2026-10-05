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
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code slack} sink. POSTs a small {@code {"text": ...}} JSON body to a configured Slack incoming
 * webhook URL (opt-in via {@code cbs.dsl.notifications.slack.webhook-url}). When no URL is
 * configured the sink records a {@code noop} so rules remain configurable and testable without a
 * real webhook.
 *
 * <p>
 * Slack returns either a plain {@code ok} body or JSON such as {@code {"ok":false,"error":"..."}};
 * any non-2xx status or {@code ok:false} body is converted into a {@link FiringOutcome#failure}
 * carrying a sanitized response snippet.
 */
@Slf4j
public class SlackRuleSink implements NotificationSink {

  static final String NOOP_DETAIL = "slack webhook not configured";

  private static final String OK_FALSE = "{\"ok\":false";

  private final HttpClient httpClient;

  private final ObjectMapper objectMapper;

  private final Duration timeout;

  private final WebhookProperties webhookProperties;

  private final @Nullable String webhookUrl;

  public SlackRuleSink(HttpClient httpClient, ObjectMapper objectMapper, Duration timeout,
          WebhookProperties webhookProperties, @Nullable String webhookUrl) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.timeout = timeout;
    this.webhookProperties = webhookProperties;
    this.webhookUrl = webhookUrl;
  }

  @Override
  public String sinkType() {
    return "slack";
  }

  @Override
  public FiringOutcome deliver(NotificationRuleEntity rule, NotificationActionDto action,
          DomainEvent event) {
    if (webhookUrl == null || webhookUrl.isBlank()) {
      return FiringOutcome.noop(NOOP_DETAIL, 0L);
    }
    String reason = WebhookUrlGuard.sinkRejectionReason(webhookUrl, webhookProperties);
    if (reason != null) {
      log.warn("Rejecting slack sink delivery: {}", reason);
      return FiringOutcome.failure(reason, 0L);
    }
    Instant started = Instant.now();
    try {
      String text = buildText(rule, event);
      byte[] body = objectMapper.writeValueAsBytes(Map.of("text", text));
      HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
              .header("Content-Type", "application/json")
              .timeout(timeout)
              .POST(HttpRequest.BodyPublishers.ofByteArray(body))
              .build();
      HttpResponse<String> response = httpClient.send(request,
              HttpResponse.BodyHandlers.ofString());
      long durationMs = Duration.between(started, Instant.now()).toMillis();
      String bodyPreview = response.body() == null ? "" : response.body();
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return FiringOutcome.failure("http " + response.statusCode() + " " + snippet(bodyPreview),
                durationMs);
      }
      if (looksLikeOkFalse(bodyPreview)) {
        return FiringOutcome.failure("slack ok=false " + snippet(bodyPreview), durationMs);
      }
      return FiringOutcome.success("http " + response.statusCode(), durationMs);
    } catch (Exception ex) {
      long durationMs = Duration.between(started, Instant.now()).toMillis();
      return FiringOutcome.failure(ex.getMessage(), durationMs);
    }
  }

  private static String buildText(NotificationRuleEntity rule, DomainEvent event) {
    return "Rule '" + rule.name() + "': " + event.eventType() + " on " + event.aggregateType()
            + "/" + event.aggregateId();
  }

  private static boolean looksLikeOkFalse(String body) {
    String trimmed = body.strip();
    return trimmed.startsWith(OK_FALSE) || trimmed.equals("{\"ok\":false}");
  }

  private static String snippet(String body) {
    if (body == null || body.isEmpty()) {
      return "";
    }
    String sanitized = body.replaceAll("\\s+", " ").strip();
    return sanitized.length() > 200 ? sanitized.substring(0, 200) + "..." : sanitized;
  }
}
