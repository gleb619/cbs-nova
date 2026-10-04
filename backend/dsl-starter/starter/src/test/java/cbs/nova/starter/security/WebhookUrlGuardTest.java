package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.webhook.WebhookProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit specs for {@link WebhookUrlGuard} (T811). Verifies the shared {@code UrlSafetyValidator} is
 * wired with the webhook/notification-sink knobs and that rejection messages never echo URL
 * userinfo.
 */
class WebhookUrlGuardTest {

  private WebhookProperties strictProperties() {
    WebhookProperties properties = new WebhookProperties();
    properties.setAllowedSchemes(List.of("https"));
    properties.setAllowedHosts(List.of());
    properties.setAllowPlainHttp(false);
    properties.setBlockPrivateAddresses(true);
    return properties;
  }

  @Test
  void publicHttpsUrlPasses() {
    WebhookProperties properties = strictProperties();
    assertThatCode(() -> WebhookUrlGuard.validateSubscription(
            "https://api.example.com/webhook", properties))
            .doesNotThrowAnyException();
  }

  @Test
  void privateIpIsRejected() {
    WebhookProperties properties = strictProperties();
    assertThatThrownBy(() -> WebhookUrlGuard.validateSink(
            "https://192.168.1.1/admin", properties))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("notification sink url")
            .hasMessageContaining("site-local");
  }

  @Test
  void loopbackIsRejected() {
    WebhookProperties properties = strictProperties();
    assertThatThrownBy(() -> WebhookUrlGuard.validateSubscription(
            "https://127.0.0.1/health", properties))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("loopback");
  }

  @Test
  void linkLocalIsRejected() {
    WebhookProperties properties = strictProperties();
    assertThatThrownBy(() -> WebhookUrlGuard.validateSink(
            "https://169.254.169.254/metadata", properties))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("link-local");
  }

  @Test
  void fileSchemeIsRejected() {
    WebhookProperties properties = strictProperties();
    assertThatThrownBy(() -> WebhookUrlGuard.validateSubscription(
            "file:///etc/passwd", properties))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scheme");
  }

  @Test
  void gopherSchemeIsRejected() {
    WebhookProperties properties = strictProperties();
    assertThatThrownBy(() -> WebhookUrlGuard.validateSink(
            "gopher://gopher.example/1", properties))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scheme");
  }

  @Test
  void userinfoIsRedactedInRejectionMessage() {
    WebhookProperties properties = strictProperties();
    assertThatThrownBy(() -> WebhookUrlGuard.validateSubscription(
            "https://user:secret@127.0.0.1/internal", properties))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageNotContaining("secret")
            .hasMessageNotContaining("user:secret@");
  }

  @Test
  void allowedHostPasses() {
    WebhookProperties properties = new WebhookProperties();
    properties.setAllowedSchemes(List.of("https"));
    properties.setAllowedHosts(List.of("hooks.example.com"));
    properties.setAllowPlainHttp(false);
    properties.setBlockPrivateAddresses(true);

    assertThatCode(() -> WebhookUrlGuard.validateSink(
            "https://hooks.example.com/alert", properties))
            .doesNotThrowAnyException();
  }

  @Test
  void allowedHostWildcardMatchesSubdomain() {
    WebhookProperties properties = new WebhookProperties();
    properties.setAllowedSchemes(List.of("https"));
    properties.setAllowedHosts(List.of("*.example.com"));
    properties.setAllowPlainHttp(false);
    properties.setBlockPrivateAddresses(true);

    assertThatCode(() -> WebhookUrlGuard.validateSubscription(
            "https://v2.example.com/hook", properties))
            .doesNotThrowAnyException();
  }

  @Test
  void rejectionReasonReturnsSanitizedMessage() {
    WebhookProperties properties = strictProperties();
    String reason = WebhookUrlGuard.subscriptionRejectionReason(
            "https://user:secret@127.0.0.1/internal", properties);

    assertThat(reason).isNotNull()
            .contains("webhook subscription url")
            .contains("loopback")
            .doesNotContain("secret")
            .doesNotContain("user:secret@");
  }

  @Test
  void rejectionReasonReturnsNullForSafeUrl() {
    WebhookProperties properties = strictProperties();
    assertThat(WebhookUrlGuard.sinkRejectionReason("https://api.example.com/x", properties))
            .isNull();
  }

  @Test
  void sanitizeStripsUserinfo() {
    assertThat(WebhookUrlGuard.sanitize("https://user:secret@api.example.com/hook"))
            .isEqualTo("https://api.example.com/hook");
  }

  @Test
  void sanitizeHandlesUnparsableUrl() {
    assertThat(WebhookUrlGuard.sanitize("not a url")).isEqualTo("unparsable-url");
  }
}
