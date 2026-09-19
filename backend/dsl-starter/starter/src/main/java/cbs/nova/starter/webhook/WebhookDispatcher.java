package cbs.nova.starter.webhook;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import cbs.nova.starter.core.StarterConstants;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RequiredArgsConstructor
public class WebhookDispatcher {

  private final WebhookProperties properties;

  private final ObjectMapper objectMapper;

  private final ThreadPoolTaskExecutor deliveryExecutor;

  private final HttpClient httpClient;

  private final Map<SubscriptionKey, WebhookDeliveryInfo> outcomes = new ConcurrentHashMap<>();

  private final Optional<WebhookDeliveryRecordRepository> deliveryRepository;

  public void onRunComplete(String runId, String processName, String status,
          Instant startedAt, Instant finishedAt, @Nullable String error) {
    if (!properties.isEnabled() || properties.getSubscriptions().isEmpty()) {
      return;
    }

    WebhookPayload payload = new WebhookPayload(
            StarterConstants.WEBHOOK_EVENT_RUN_COMPLETED,
            runId,
            processName,
            status,
            startedAt.toString(),
            finishedAt.toString(),
            error);

    for (WebhookSubscription subscription : properties.getSubscriptions()) {
      if (!matches(subscription, processName, status)) {
        continue;
      }
      deliveryExecutor.execute(() -> deliver(subscription, payload));
    }
  }

  public Collection<WebhookDeliveryInfo> deliveryInfos() {
    return Collections.unmodifiableCollection(outcomes.values());
  }

  Map<SubscriptionKey, WebhookDeliveryInfo> outcomeMap() {
    return Collections.unmodifiableMap(outcomes);
  }

  /**
   * Rule-based delivery path (T565): delivers {@code payload} to an explicit {@code url} with an
   * optional HMAC {@code secret} and the given {@code X-Cbs-Event} header value, WITHOUT touching
   * the static {@code cbs.nova.dsl.webhooks.subscriptions} list. Shares the transport with the
   * static path (signing, https enforcement, bounded retries) but never writes into
   * {@code dsl_webhook_deliveries} — callers audit the returned result themselves.
   */
  public WebhookDeliveryResult dispatchTo(String url, @Nullable String secret, String eventType,
          Object payload) {
    Instant deliveryStarted = Instant.now();
    byte[] body;
    try {
      body = objectMapper.writeValueAsBytes(payload);
    } catch (Exception ex) {
      log.warn("Failed to serialize notification payload for {}", url, ex);
      return new WebhookDeliveryResult("serialization_failed", 0, ex.getMessage(), 0L);
    }

    if (!isUrlAllowed(url)) {
      log.warn("Rejecting notification delivery to non-https URL: {}", url);
      return new WebhookDeliveryResult("rejected", 0,
              "Non-https URL not allowed: " + truncate(url), 0L);
    }

    return sendWithRetries(url, secret, eventType, body, deliveryStarted);
  }

  private boolean matches(WebhookSubscription subscription, String processName, String status) {
    if (!matchesPattern(processName, subscription.definitionPattern())) {
      return false;
    }
    Set<String> statuses = subscription.statuses();
    if (statuses == null || statuses.isEmpty()) {
      return true;
    }
    return statuses.contains(status);
  }

  private boolean matchesPattern(String processName, String pattern) {
    try {
      PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
      return matcher.matches(Path.of(processName));
    } catch (Exception ex) {
      return false;
    }
  }

  private void deliver(WebhookSubscription subscription, WebhookPayload payload) {
    Instant deliveryStarted = Instant.now();
    String url = subscription.url();
    byte[] body;
    try {
      body = objectMapper.writeValueAsBytes(payload);
    } catch (Exception ex) {
      recordOutcome(subscription, "serialization_failed", 0, ex.getMessage(), 0L);
      log.warn("Failed to serialize webhook payload for {}", url, ex);
      return;
    }

    if (!isUrlAllowed(url)) {
      recordOutcome(subscription, "rejected", 0,
              "Non-https URL not allowed: " + truncate(url), 0L);
      log.warn("Rejecting webhook delivery to non-https URL: {}", url);
      return;
    }

    WebhookDeliveryResult result = sendWithRetries(url, subscription.secret(),
            StarterConstants.WEBHOOK_EVENT_RUN_COMPLETED, body, deliveryStarted);
    recordOutcome(subscription, result.status(), result.attempts(), result.error(),
            result.durationMs());
  }

  /**
   * Shared retry loop used by both the static subscription path and the rule-based
   * {@link #dispatchTo} path. {@code eventType} becomes the {@code X-Cbs-Event} header.
   */
  private WebhookDeliveryResult sendWithRetries(String url, @Nullable String secret,
          String eventType, byte[] body, Instant deliveryStarted) {
    int maxAttempts = Math.max(1, properties.getMaxRetries());
    int lastStatus = -1;
    @Nullable
    String lastError = null;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      HttpRequest request = buildRequest(url, secret, eventType, body);
      try {
        HttpResponse<Void> response = httpClient.send(request,
                HttpResponse.BodyHandlers.discarding());
        lastStatus = response.statusCode();
        if (isSuccess(lastStatus)) {
          return new WebhookDeliveryResult(String.valueOf(lastStatus), attempt, null,
                  durationMs(deliveryStarted));
        }
        if (isTerminalClientError(lastStatus)) {
          return new WebhookDeliveryResult(String.valueOf(lastStatus), attempt, null,
                  durationMs(deliveryStarted));
        }
        if (attempt < maxAttempts) {
          sleep(backoffForAttempt(attempt));
        }
      } catch (IOException ex) {
        lastError = ex.getMessage();
        if (attempt < maxAttempts) {
          sleep(backoffForAttempt(attempt));
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return new WebhookDeliveryResult("interrupted", attempt, ex.getMessage(),
                durationMs(deliveryStarted));
      }
    }

    return new WebhookDeliveryResult(
            lastStatus >= 0 ? String.valueOf(lastStatus) : "failed",
            maxAttempts,
            lastError,
            durationMs(deliveryStarted));
  }

  private boolean isUrlAllowed(String url) {
    try {
      URL parsed = URI.create(url).toURL();
      String protocol = parsed.getProtocol();
      if ("https".equalsIgnoreCase(protocol)) {
        return true;
      }
      return "http".equalsIgnoreCase(protocol) && properties.isAllowPlainHttp();
    } catch (Exception ex) {
      return false;
    }
  }

  private HttpRequest buildRequest(String url, @Nullable String secret, String eventType,
          byte[] body) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .header(StarterConstants.WEBHOOK_TIMESTAMP_HEADER,
                    String.valueOf(Instant.now().getEpochSecond()))
            .header(StarterConstants.WEBHOOK_EVENT_HEADER, eventType)
            .timeout(properties.getTimeout())
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));

    if (secret != null && !secret.isBlank()) {
      String signature = computeSignature(secret, body);
      if (signature != null) {
        builder.header(StarterConstants.WEBHOOK_SIGNATURE_HEADER, "sha256=" + signature);
      }
    }
    return builder.build();
  }

  private @Nullable String computeSignature(String secret, byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      SecretKeySpec key = new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256");
      mac.init(key);
      byte[] digest = mac.doFinal(body);
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      log.warn("Failed to compute webhook HMAC signature", ex);
      return null;
    }
  }

  private boolean isSuccess(int status) {
    return status >= 200 && status < 400;
  }

  private boolean isTerminalClientError(int status) {
    return status >= 400 && status < 500 && status != 429;
  }

  private Duration backoffForAttempt(int attempt) {
    return properties.getRetryBackoff().multipliedBy(attempt);
  }

  private void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(ex);
    }
  }

  private void recordOutcome(WebhookSubscription subscription, String status, int attempts,
          @Nullable String error, long durationMs) {
    Instant occurredAt = Instant.now();
    SubscriptionKey key = new SubscriptionKey(subscription.url(), subscription.definitionPattern());
    outcomes.put(key, new WebhookDeliveryInfo(
            subscription.url(),
            subscription.definitionPattern(),
            status,
            attempts,
            error,
            occurredAt));
    deliveryRepository.ifPresent(repository -> {
      try {
        // URLs longer than the column width are truncated rather than rejected so that the delivery
        // still leaves an audit trace. The same applies to last_error.
        String url = subscription.url().length() > StarterConstants.WEBHOOK_URL_MAX_LENGTH
                ? subscription.url().substring(0, StarterConstants.WEBHOOK_URL_MAX_LENGTH)
                : subscription.url();
        repository.insert(
                new WebhookDeliveryRecord(null, occurredAt, subscription.definitionPattern(),
                        StarterConstants.WEBHOOK_EVENT_RUN_COMPLETED,
                        url, status, attempts, error, durationMs));
      } catch (Exception ex) {
        log.warn("Failed to persist webhook delivery outcome", ex);
      }
    });
  }

  private static long durationMs(Instant started) {
    return Duration.between(started, Instant.now()).toMillis();
  }

  private static String truncate(String value) {
    if (value.length() <= StarterConstants.WEBHOOK_URL_MAX_LENGTH) {
      return value;
    }
    return value.substring(0, StarterConstants.WEBHOOK_URL_MAX_LENGTH);
  }

  private record SubscriptionKey(String url, String definitionPattern) {
  }
}
