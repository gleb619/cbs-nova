package cbs.nova.starter.security;

import cbs.nova.starter.config.properties.HttpCallProperties;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Validates outbound {@code httpCall} URLs before a request is built: scheme allowlist,
 * private-address block (loopback / link-local / site-local / any-local / multicast) and an
 * optional host allowlist (exact or {@code *.suffix} match).
 *
 * <p>
 * Logic mirrors {@code cbs.nova.dsl.builder.service.RepoUrlValidator} (T428). TODO: unify into a
 * shared validator instead of keeping module-local duplicates.
 */
public final class OutboundUrlValidator {

  private OutboundUrlValidator() {
  }

  public static void validate(String url, HttpCallProperties properties) {
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("httpCall url is not a valid URI", e);
    }
    if (uri.isOpaque()) {
      throw reject(uri, "opaque URIs are not allowed");
    }
    var scheme = uri.getScheme();
    if (scheme == null || scheme.isBlank()) {
      throw reject(uri, "missing scheme");
    }
    if (properties.allowedSchemes().stream()
            .noneMatch(allowed -> allowed.equalsIgnoreCase(scheme))) {
      throw reject(uri, "scheme '" + scheme + "' is not allowed");
    }
    var host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw reject(uri, "missing host");
    }
    var allowedHosts = properties.allowedHosts();
    if (!allowedHosts.isEmpty()
            && allowedHosts.stream().noneMatch(entry -> matchesHost(entry, host))) {
      throw reject(uri, "host is not allowed");
    }
    if (properties.blockPrivateAddresses()) {
      blockIfPrivateAddress(uri, host);
    }
  }

  private static void blockIfPrivateAddress(URI uri, String host) {
    final InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host);
    } catch (UnknownHostException e) {
      // Best-effort check: an unresolvable host cannot be classified, so the address guard is
      // skipped and the request itself will fail later.
      return;
    }
    for (InetAddress address : addresses) {
      String reason = blockedReason(address);
      if (reason != null) {
        throw reject(uri, "host resolves to a " + reason + " address");
      }
    }
  }

  private static String blockedReason(InetAddress address) {
    if (address.isLoopbackAddress()) {
      return "loopback";
    }
    if (address.isLinkLocalAddress()) {
      return "link-local";
    }
    if (address.isSiteLocalAddress()) {
      return "site-local";
    }
    if (address.isAnyLocalAddress()) {
      return "wildcard";
    }
    if (address.isMulticastAddress()) {
      return "multicast";
    }
    return null;
  }

  private static boolean matchesHost(String entry, String host) {
    if (entry == null) {
      return false;
    }
    var expected = entry.toLowerCase(Locale.ROOT);
    var actual = host.toLowerCase(Locale.ROOT);
    if (expected.startsWith("*.")) {
      return actual.endsWith(expected.substring(1)) && actual.length() > expected.length() - 1;
    }
    return actual.equals(expected);
  }

  private static IllegalArgumentException reject(URI uri, String reason) {
    return new IllegalArgumentException("httpCall url '" + sanitize(uri) + "' rejected: " + reason);
  }

  /**
   * Renders a URI for error messages, stripping any userinfo (credentials) so they are never leaked
   * into logs or failure results.
   */
  public static String sanitize(URI uri) {
    var sanitized = new StringBuilder();
    if (uri.getScheme() != null) {
      sanitized.append(uri.getScheme()).append("://");
    }
    if (uri.getHost() != null) {
      sanitized.append(uri.getHost());
    } else if (uri.getAuthority() != null) {
      var authority = uri.getAuthority();
      var at = authority.indexOf('@');
      sanitized.append(at >= 0 ? authority.substring(at + 1) : authority);
    }
    if (uri.getPort() >= 0) {
      sanitized.append(':').append(uri.getPort());
    }
    if (uri.getPath() != null) {
      sanitized.append(uri.getPath());
    }
    return sanitized.toString();
  }
}
