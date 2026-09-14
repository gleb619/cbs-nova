package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

final class RepoUrlValidator {

  private RepoUrlValidator() {
  }

  static void validate(String repoUrl, DslBuilderProperties properties) {
    URI uri;
    try {
      uri = new URI(repoUrl);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid repoUrl: not a valid URI", e);
    }
    if (uri.isOpaque()) {
      throw reject(uri, "opaque URIs are not allowed");
    }
    var scheme = uri.getScheme();
    if (scheme == null || scheme.isBlank()) {
      throw reject(uri, "missing scheme");
    }
    var allowedSchemes = properties.allowedRepoSchemes();
    if (allowedSchemes.stream().noneMatch(allowed -> allowed.equalsIgnoreCase(scheme))) {
      throw reject(uri, "scheme '" + scheme + "' is not allowed");
    }
    if ("http".equalsIgnoreCase(scheme) && !properties.allowPlainHttpRepo()) {
      throw reject(uri, "plain http repo URLs are disabled");
    }
    var host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw reject(uri, "missing host");
    }
    var allowedHosts = properties.allowedRepoHosts();
    if (!allowedHosts.isEmpty()
            && allowedHosts.stream().noneMatch(entry -> matchesHost(entry, host))) {
      throw reject(uri, "host is not allowed");
    }
    try {
      var address = InetAddress.getByName(host);
      if (address.isLoopbackAddress()
              || address.isLinkLocalAddress()
              || address.isSiteLocalAddress()
              || address.isAnyLocalAddress()) {
        throw reject(uri,
                "host resolves to a loopback, link-local, site-local or wildcard address");
      }
    } catch (UnknownHostException e) {
      // Best-effort check: an unresolvable host cannot be classified, so the address guard is
      // skipped and the clone itself will fail later.
    }
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
    return new IllegalArgumentException("repoUrl '" + sanitize(uri) + "' rejected: " + reason);
  }

  private static String sanitize(URI uri) {
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
