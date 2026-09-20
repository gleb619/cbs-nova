package cbs.nova.dsl.utils;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * Shared SSRF guard for outbound URLs (httpCall) and repo clone URLs. Performs a URI syntax check,
 * rejects opaque URIs, enforces a case-insensitive scheme allowlist, optionally gates plain http,
 * optionally enforces a host allowlist (exact or {@code *.suffix} match) and optionally blocks
 * private addresses (loopback / link-local / site-local / any-local, plus multicast when
 * {@link Config#blockMulticast()} is set).
 *
 * <p>
 * Callers supply a {@code label} (e.g. {@code "httpCall url"} or {@code "repoUrl"}) so error
 * messages stay distinguishable per call site.
 */
public final class UrlSafetyValidator {

  private UrlSafetyValidator() {
  }

  public record Config(
          List<String> allowedSchemes,
          List<String> allowedHosts,
          boolean allowPlainHttp,
          boolean blockPrivateAddresses,
          boolean blockMulticast) {

    public Config {
      allowedSchemes = allowedSchemes == null
              ? List.of("https")
              : List.copyOf(allowedSchemes);
      allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
    }
  }

  public static void validate(String url, Config config, String label) {
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid " + label + ": not a valid URI", e);
    }
    if (uri.isOpaque()) {
      throw reject(uri, label, "opaque URIs are not allowed");
    }
    var scheme = uri.getScheme();
    if (scheme == null || scheme.isBlank()) {
      throw reject(uri, label, "missing scheme");
    }
    if (config.allowedSchemes().stream()
            .noneMatch(allowed -> allowed.equalsIgnoreCase(scheme))) {
      throw reject(uri, label, "scheme '" + scheme + "' is not allowed");
    }
    if ("http".equalsIgnoreCase(scheme) && !config.allowPlainHttp()) {
      throw reject(uri, label, "plain http URLs are disabled");
    }
    var host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw reject(uri, label, "missing host");
    }
    var allowedHosts = config.allowedHosts();
    if (!allowedHosts.isEmpty()
            && allowedHosts.stream().noneMatch(entry -> matchesHost(entry, host))) {
      throw reject(uri, label, "host is not allowed");
    }
    if (config.blockPrivateAddresses()) {
      blockIfPrivateAddress(uri, config, label, host);
    }
  }

  private static void blockIfPrivateAddress(URI uri, Config config, String label, String host) {
    final InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host);
    } catch (UnknownHostException e) {
      // Best-effort check: an unresolvable host cannot be classified, so the address guard is
      // skipped and the request itself will fail later.
      return;
    }
    for (InetAddress address : addresses) {
      String reason = blockedReason(address, config.blockMulticast());
      if (reason != null) {
        throw reject(uri, label, "host resolves to a " + reason + " address");
      }
    }
  }

  private static String blockedReason(InetAddress address, boolean blockMulticast) {
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
    if (blockMulticast && address.isMulticastAddress()) {
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

  private static IllegalArgumentException reject(URI uri, String label, String reason) {
    return new IllegalArgumentException(
            label + " '" + sanitize(uri) + "' rejected: " + reason);
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
