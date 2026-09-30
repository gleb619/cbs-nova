package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.config.properties.HttpCallProperties;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Direct unit specs for the {@link OutboundUrlValidator} adapter (T738).
 *
 * <p>
 * Pinned behaviours:
 * <ul>
 * <li>Default {@link HttpCallProperties} (https+http allowed, private addresses blocked, no host
 * allowlist) accepts a public HTTPS URL and rejects loopback / private / scheme-mismatch inputs
 * with an {@link IllegalArgumentException} that carries the {@code httpCall url} label.</li>
 * <li>{@link OutboundUrlValidator#sanitize(URI)} strips userinfo from a URI so credentials never
 * leak into error messages.</li>
 * </ul>
 *
 * <p>
 * SSRF findings are report-only per the T738 plan: do not fix the validator here.
 */
class OutboundUrlValidatorTest {

  @Nested
  class DefaultHttpCallProperties {

    private final HttpCallProperties properties = new HttpCallProperties(null, true, null);

    @Test
    void publicHttpsHostIsAccepted() {
      assertThatCode(() -> OutboundUrlValidator.validate("https://api.example.com/v1/x",
              properties))
              .doesNotThrowAnyException();
    }

    @Test
    void loopbackHostIsRejected() {
      assertThatThrownBy(
              () -> OutboundUrlValidator.validate("https://127.0.0.1/health",
                      properties))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("httpCall url")
              .hasMessageContaining("loopback");
    }

    @Test
    void localhostResolvesToLoopbackAndIsRejected() {
      assertThatThrownBy(
              () -> OutboundUrlValidator.validate("http://localhost:8080/admin",
                      properties))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("loopback");
    }

    @Test
    void unknownSchemeIsRejected() {
      assertThatThrownBy(
              () -> OutboundUrlValidator.validate("ftp://files.example.com/data",
                      properties))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("scheme");
    }

    @Test
    void missingHostIsRejected() {
      assertThatThrownBy(
              () -> OutboundUrlValidator.validate("https:///nohost", properties))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("host");
    }

    @Test
    void credentialsAreRedactedInRejectionMessage() {
      assertThatThrownBy(
              () -> OutboundUrlValidator.validate(
                      "https://user:secret@127.0.0.1/internal", properties))
              .isInstanceOf(IllegalArgumentException.class)
              // userinfo must NOT appear in the error string.
              .hasMessageNotContaining("secret")
              .hasMessageNotContaining("user:secret@");
    }
  }

  @Nested
  class CustomSchemesAndHostAllowlist {

    @Test
    void hostAllowlistExactMatchIsAccepted() {
      HttpCallProperties properties = new HttpCallProperties(
              List.of("https"), true, List.of("api.allowed.example"));
      assertThatCode(() -> OutboundUrlValidator.validate("https://api.allowed.example/v1",
              properties))
              .doesNotThrowAnyException();
    }

    @Test
    void hostAllowlistMissIsRejected() {
      HttpCallProperties properties = new HttpCallProperties(
              List.of("https"), true, List.of("api.allowed.example"));
      assertThatThrownBy(
              () -> OutboundUrlValidator.validate("https://api.other.example/v1",
                      properties))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("host is not allowed");
    }

    @Test
    void hostAllowlistSuffixMatchAcceptsSubdomain() {
      HttpCallProperties properties = new HttpCallProperties(
              List.of("https"), true, List.of("*.allowed.example"));
      assertThatCode(() -> OutboundUrlValidator.validate("https://v2.allowed.example/v1",
              properties))
              .doesNotThrowAnyException();
    }

    @Test
    void hostAllowlistSuffixDoesNotMatchApex() {
      // Apex "allowed.example" must not be matched by the "*.allowed.example" wildcard suffix.
      HttpCallProperties properties = new HttpCallProperties(
              List.of("https"), true, List.of("*.allowed.example"));
      assertThatThrownBy(
              () -> OutboundUrlValidator.validate("https://allowed.example/v1",
                      properties))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("host is not allowed");
    }

    @Test
    void plainHttpIsRejectedWhenSchemeAllowlistRestrictsToHttps() {
      HttpCallProperties properties = new HttpCallProperties(
              List.of("https"), true, List.of());
      assertThatThrownBy(
              () -> OutboundUrlValidator.validate("http://api.allowed.example/v1",
                      properties))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("scheme");
    }

    @Test
    void privateAddressCheckDisabledAcceptsLoopback() {
      // blockPrivateAddresses=false: the validator no longer resolves the host, so loopback is
      // accepted. The host-allowlist gate is the only filter in this configuration.
      HttpCallProperties properties = new HttpCallProperties(
              List.of("https"), false, List.of());
      assertThatCode(() -> OutboundUrlValidator.validate("https://127.0.0.1/health",
              properties))
              .doesNotThrowAnyException();
    }
  }

  @Nested
  class Sanitize {

    @Test
    void userinfoIsStrippedFromUri() {
      URI uri = URI.create("https://user:secret@api.example.com/v1");
      assertThat(OutboundUrlValidator.sanitize(uri))
              .doesNotContain("user")
              .doesNotContain("secret")
              .contains("api.example.com");
    }

    @Test
    void plainUriIsReturnedUnchanged() {
      URI uri = URI.create("https://api.example.com/v1/x");
      assertThat(OutboundUrlValidator.sanitize(uri)).isEqualTo("https://api.example.com/v1/x");
    }
  }
}
