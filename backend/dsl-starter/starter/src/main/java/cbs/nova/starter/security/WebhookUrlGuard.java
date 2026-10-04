package cbs.nova.starter.security;

import cbs.nova.dsl.utils.UrlSafetyValidator;
import cbs.nova.starter.webhook.WebhookProperties;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;

/**
 * T811 SSRF guard for operator-supplied outbound URLs: static webhook subscriptions
 * ({@code cbs.nova.dsl.webhooks.subscriptions}) and notification rule action URLs ({@code webhook},
 * {@code slack}, {@code pagerduty}).
 *
 * <p>
 * Thin adapter over the shared {@link UrlSafetyValidator} — same rules as the {@code httpCall}
 * helper, only the knobs and the label differ. All rules come from {@link WebhookProperties}:
 * {@code allowed-schemes}, {@code allowed-hosts}, {@code block-private-addresses}, and
 * {@code allow-plain-http} (which is mapped onto the validator's plain-http gate, so the existing
 * "https only" webhook default is preserved).
 *
 * <p>
 * {@link WebhookProperties#isBlockPrivateAddresses()} defaults to {@code false} so existing
 * dev/test flows that deliver to a local WireMock/container keep working; operators tighten it in
 * production.
 */
public final class WebhookUrlGuard {

  public static final String SUBSCRIPTION_LABEL = "webhook subscription url";

  public static final String SINK_LABEL = "notification sink url";

  private WebhookUrlGuard() {
  }

  /**
   * Builds the validator config from the webhook properties. {@code allowPlainHttp} is forwarded as
   * the validator's plain-http gate, which keeps the historical behaviour ("only https unless
   * {@code allow-plain-http=true}") expressed in one place.
   */
  public static UrlSafetyValidator.Config configOf(WebhookProperties properties) {
    return new UrlSafetyValidator.Config(
            properties.getAllowedSchemes(),
            properties.getAllowedHosts(),
            properties.isAllowPlainHttp(),
            properties.isBlockPrivateAddresses(),
            true);
  }

  public static void validateSubscription(String url, WebhookProperties properties) {
    validate(url, properties, SUBSCRIPTION_LABEL);
  }

  public static void validateSink(String url, WebhookProperties properties) {
    validate(url, properties, SINK_LABEL);
  }

  private static void validate(String url, WebhookProperties properties, String label) {
    OutboundUrlValidator.validate(url, configOf(properties), label);
  }

  /**
   * Dispatch-time re-check for a URL that was already validated on save. Returns {@code null} when
   * the URL is still safe, or the sanitized rejection reason otherwise. Never throws — stored rows
   * predate the guard and DNS can change, so a failure must degrade into a delivery failure rather
   * than an exception on the publish path.
   */
  public static String subscriptionRejectionReason(String url, WebhookProperties properties) {
    return rejectionReason(url, properties, SUBSCRIPTION_LABEL);
  }

  public static String sinkRejectionReason(String url, WebhookProperties properties) {
    return rejectionReason(url, properties, SINK_LABEL);
  }

  public static String rejectionReason(String url, WebhookProperties properties, String label) {
    try {
      validate(url, properties, label);
      return null;
    } catch (IllegalArgumentException ex) {
      return ex.getMessage();
    }
  }

  /**
   * Renders a URL for logs and persisted failure rows: userinfo (credentials) is stripped by
   * {@link UrlSafetyValidator#sanitize(URI)}.
   */
  public static String sanitize(String url) {
    try {
      return OutboundUrlValidator.sanitize(URI.create(url));
    } catch (IllegalArgumentException ex) {
      return "unparsable-url";
    }
  }

  /**
   * Permissive config used by manually wired call sites (tests, examples) that must keep their
   * pre-guard behaviour: any scheme, any host, no private-address blocking.
   */
  public static WebhookProperties permissiveProperties() {
    WebhookProperties properties = new WebhookProperties();
    properties.setAllowedSchemes(List.of("https", "http"));
    properties.setAllowedHosts(List.of());
    properties.setAllowPlainHttp(true);
    properties.setBlockPrivateAddresses(false);
    return properties;
  }

  /**
   * Blocks redirect-following clients from re-introducing an SSRF hop: the dispatcher and the chat
   * sinks never follow redirects, so the only guard point is the initial URL.
   */
  public static HttpClient newHttpClient(WebhookProperties properties) {
    return HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(properties.getTimeout())
            .build();
  }
}
