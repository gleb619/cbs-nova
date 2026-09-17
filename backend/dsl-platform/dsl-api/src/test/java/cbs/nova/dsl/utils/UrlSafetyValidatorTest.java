package cbs.nova.dsl.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class UrlSafetyValidatorTest {

  private static UrlSafetyValidator.Config config(
          List<String> schemes, List<String> hosts, boolean allowPlainHttp,
          boolean blockPrivate, boolean blockMulticast) {
    return new UrlSafetyValidator.Config(schemes, hosts, allowPlainHttp, blockPrivate,
            blockMulticast);
  }

  private static UrlSafetyValidator.Config defaults() {
    return config(null, null, false, true, true);
  }

  @Test
  void allowsHttpsPublicHost() {
    assertThatCode(() -> UrlSafetyValidator.validate("https://github.com/org/repo.git", defaults(),
            "repoUrl")).doesNotThrowAnyException();
  }

  @Test
  void rejectsUnparseableUrl() {
    assertThatThrownBy(() -> UrlSafetyValidator.validate("https://exa mple.com/\\repo", defaults(),
            "repoUrl"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a valid URI");
  }

  @Test
  void rejectsOpaqueUri() {
    assertThatThrownBy(() -> UrlSafetyValidator.validate("https:example.com/x", defaults(),
            "httpCall url"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("opaque");
  }

  @Test
  void rejectsMissingScheme() {
    assertThatThrownBy(() -> UrlSafetyValidator.validate("github.com/org/repo.git", defaults(),
            "repoUrl"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing scheme");
  }

  @Test
  void rejectsMissingHost() {
    assertThatThrownBy(() -> UrlSafetyValidator.validate("https:///path", defaults(), "repoUrl"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing host");
  }

  @Test
  void rejectsDisallowedScheme() {
    assertThatThrownBy(() -> UrlSafetyValidator.validate("ftp://example.com/file", defaults(),
            "httpCall url"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("'ftp'");
  }

  @Test
  void schemeAllowlistIsCaseInsensitive() {
    var cfg = config(List.of("HTTPS"), null, false, true, true);
    assertThatCode(() -> UrlSafetyValidator.validate("https://example.com/x", cfg, "httpCall url"))
            .doesNotThrowAnyException();
  }

  @Test
  void rejectsPlainHttpByDefault() {
    assertThatThrownBy(() -> UrlSafetyValidator.validate("http://git.example/repo.git", defaults(),
            "repoUrl"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("http");
  }

  @Test
  void allowsPlainHttpWhenEnabled() {
    var cfg = config(List.of("https", "http"), null, true, true, true);
    assertThatCode(() -> UrlSafetyValidator.validate("http://git.example/repo.git", cfg, "repoUrl"))
            .doesNotThrowAnyException();
  }

  @Test
  void rejectsHostOutsideAllowlist() {
    var cfg = config(null, List.of("github.com", "*.internal.example"), false, true, true);

    assertThatCode(() -> UrlSafetyValidator.validate("https://github.com/org/repo.git", cfg,
            "repoUrl")).doesNotThrowAnyException();
    assertThatCode(() -> UrlSafetyValidator.validate("https://git.internal.example/org/repo.git",
            cfg, "repoUrl")).doesNotThrowAnyException();
    assertThatThrownBy(() -> UrlSafetyValidator.validate("https://gitlab.com/org/repo.git", cfg,
            "repoUrl"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("host is not allowed");
  }

  @Test
  void wildcardHostEntryDoesNotMatchApex() {
    var cfg = config(null, List.of("*.internal.example"), false, true, true);

    assertThatThrownBy(() -> UrlSafetyValidator.validate("https://internal.example/org/repo.git",
            cfg, "repoUrl"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("host is not allowed");
  }

  @Test
  void wildcardHostEntryDoesNotMatchLookalikeSuffix() {
    var cfg = config(null, List.of("*.example.com"), false, true, true);

    assertThatThrownBy(() -> UrlSafetyValidator.validate("https://evil-example.com/x", cfg,
            "httpCall url"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("host is not allowed");
  }

  @Test
  void blocksLoopbackIpv4AndIpv6() {
    assertThatThrownBy(() -> UrlSafetyValidator.validate("https://127.0.0.1/x", defaults(),
            "repoUrl"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("loopback");
    assertThatThrownBy(() -> UrlSafetyValidator.validate("https://[::1]/x", defaults(), "repoUrl"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("loopback");
  }

  @Test
  void blocksLinkLocalMetadataAddress() {
    assertThatThrownBy(() -> UrlSafetyValidator.validate("https://169.254.169.254/latest/meta-data",
            defaults(), "repoUrl"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("link-local");
  }

  @Test
  void blocksSiteLocalAddress() {
    assertThatThrownBy(
            () -> UrlSafetyValidator.validate("https://192.168.1.10/repo.git", defaults(),
                    "repoUrl"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("site-local");
  }

  @Test
  void blocksWildcardAndMulticastAddresses() {
    assertThatThrownBy(() -> UrlSafetyValidator.validate("https://0.0.0.0/x", defaults(),
            "httpCall url"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("wildcard");
    assertThatThrownBy(() -> UrlSafetyValidator.validate("https://224.0.0.1/x", defaults(),
            "httpCall url"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("multicast");
  }

  @Test
  void multicastNotBlockedWhenFlagOff() {
    var cfg = config(null, null, false, true, false);
    assertThatCode(() -> UrlSafetyValidator.validate("https://224.0.0.1/x", cfg, "httpCall url"))
            .doesNotThrowAnyException();
  }

  @Test
  void privateAddressesPassWhenBlockingDisabled() {
    var cfg = config(List.of("https", "http"), null, true, false, true);
    assertThatCode(() -> UrlSafetyValidator.validate("http://169.254.169.254/latest", cfg,
            "httpCall url")).doesNotThrowAnyException();
    assertThatCode(() -> UrlSafetyValidator.validate("http://[::1]/x", cfg, "httpCall url"))
            .doesNotThrowAnyException();
    assertThatCode(() -> UrlSafetyValidator.validate("http://10.0.0.5/x", cfg, "httpCall url"))
            .doesNotThrowAnyException();
  }

  @Test
  void neverLeaksUserInfoIntoRejectionMessage() {
    assertThatThrownBy(() -> UrlSafetyValidator.validate("https://user:token@127.0.0.1/x",
            defaults(), "repoUrl"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageNotContaining("user:token")
            .hasMessageNotContaining("token");

    var restricted = config(null, List.of("github.com"), false, true, true);
    assertThatThrownBy(() -> UrlSafetyValidator.validate("https://user:token@gitlab.com/x",
            restricted, "repoUrl"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageNotContaining("user:token")
            .hasMessageNotContaining("token");
  }

  @Test
  void labelDistinguishesCallSitesInMessages() {
    assertThatThrownBy(() -> UrlSafetyValidator.validate("ftp://example.com/x", defaults(),
            "httpCall url"))
            .hasMessageContaining("httpCall url");
    assertThatThrownBy(() -> UrlSafetyValidator.validate("ftp://example.com/x", defaults(),
            "repoUrl"))
            .hasMessageContaining("repoUrl");
  }

  @Test
  void sanitizeStripsUserInfo() throws Exception {
    var uri = new java.net.URI("https://user:token@example.com:8443/path");
    assertThat(UrlSafetyValidator.sanitize(uri)).isEqualTo("https://example.com:8443/path");
  }
}
