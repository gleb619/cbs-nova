package cbs.nova.starter.security;

import cbs.nova.dsl.utils.UrlSafetyValidator;
import cbs.nova.starter.config.properties.HttpCallProperties;
import java.net.URI;

/**
 * Validates outbound {@code httpCall} URLs before a request is built. Thin adapter over the shared
 * {@link UrlSafetyValidator} (T517); scheme allowlist, optional host allowlist and private-address
 * blocking are configured via {@link HttpCallProperties}.
 */
public final class OutboundUrlValidator {

  private static final String LABEL = "httpCall url";

  private OutboundUrlValidator() {
  }

  public static void validate(String url, HttpCallProperties properties) {
    var config = new UrlSafetyValidator.Config(
            properties.allowedSchemes(),
            properties.allowedHosts(),
            true,
            properties.blockPrivateAddresses(),
            true);
    validate(url, config, LABEL);
  }

  /**
   * Generic entry point for callers that already own a {@link UrlSafetyValidator.Config}. Used by
   * the webhook/notification-sink guard ({@code WebhookUrlGuard}) so the SSRF rules live in exactly
   * one place.
   */
  public static void validate(String url, UrlSafetyValidator.Config config, String label) {
    UrlSafetyValidator.validate(url, config, label);
  }

  /**
   * Renders a URI for error messages, stripping any userinfo (credentials) so they are never leaked
   * into logs or failure results.
   */
  public static String sanitize(URI uri) {
    return UrlSafetyValidator.sanitize(uri);
  }
}
