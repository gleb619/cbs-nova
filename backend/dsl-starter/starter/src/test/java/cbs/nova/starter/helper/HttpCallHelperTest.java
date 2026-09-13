package cbs.nova.starter.helper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.temporaryRedirect;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties.Level;
import cbs.nova.starter.config.properties.HttpCallProperties;
import cbs.nova.starter.helper.HttpCallHelper.HttpCallFailure;
import cbs.nova.starter.helper.HttpCallHelper.HttpCallTransportException;
import cbs.nova.starter.helper.model.HttpCallIn;
import cbs.nova.starter.helper.model.HttpCallOut;
import cbs.nova.starter.security.OutboundUrlValidator;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

class HttpCallHelperTest {

  private WireMockServer wireMock;
  private HttpCallHelper helper;
  private final ContextFactory contextFactory = new ContextFactory();

  @BeforeEach
  void setUp() {
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();
    helper = new HttpCallHelper(HttpClient.newHttpClient(),
            new CbsNovaLoggingProperties(Level.INFO, Level.INFO, true));
  }

  @AfterEach
  void tearDown() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  private String baseUrl() {
    return "http://localhost:" + wireMock.port();
  }

  private Result<HttpCallOut> execute(HttpCallIn input) {
    return execute(helper, input);
  }

  private Result<HttpCallOut> execute(HttpCallHelper helper, HttpCallIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }

  private HttpCallHelper helperWith(HttpCallProperties properties) {
    return new HttpCallHelper(HttpClient.newHttpClient(),
            new CbsNovaLoggingProperties(Level.INFO, Level.INFO, true), properties);
  }

  private static HttpCallProperties secureProperties() {
    return new HttpCallProperties(List.of("https", "http"), true, List.of());
  }

  @Test
  void successPathReturnsResponseBodyAndHeaders() {
    wireMock.stubFor(get("/hello")
            .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("X-Custom", "yes")
                    .withBody("hello world")));

    Result<HttpCallOut> result = execute(HttpCallIn.get(baseUrl() + "/hello"));

    assertThat(result.isSuccess())
            .as("result cause: %s", result.cause())
            .isTrue();
    HttpCallOut out = result.value();
    assertThat(out).isNotNull();
    assertThat(out.status()).isEqualTo(200);
    assertThat(out.bodyOrEmpty()).isEqualTo("hello world");
    assertThat(out.headersOrEmpty().get("x-custom"))
            .as("header should be returned regardless of case (JDK normalizes to lowercase)")
            .isEqualTo("yes");
    assertThat(out.errorMessage()).isNull();
  }

  @Test
  void non2xxMapsToFailureWithStatusAndBody() {
    wireMock.stubFor(get("/boom")
            .willReturn(aResponse()
                    .withStatus(503)
                    .withHeader("Retry-After", "1")
                    .withBody("upstream broken")));

    Result<HttpCallOut> result = execute(HttpCallIn.get(baseUrl() + "/boom"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause())
            .isInstanceOf(HttpCallFailure.class);
    HttpCallFailure failure = (HttpCallFailure) result.cause();
    assertThat(failure.status()).isEqualTo(503);
    assertThat(failure.body()).isEqualTo("upstream broken");
    assertThat(failure.getMessage()).contains("503");
  }

  @Test
  void customValidStatusOverridesDefault2xxCheck() {
    wireMock.stubFor(get("/accepted")
            .willReturn(aResponse()
                    .withStatus(202)
                    .withBody("accepted")));

    Result<HttpCallOut> result = execute(new HttpCallIn(
            baseUrl() + "/accepted", "GET", null, null, null, null, List.of(200, 202)));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().status()).isEqualTo(202);
  }

  @Test
  void postWithJsonBodyForwardsPayloadAndHeaders() {
    wireMock.stubFor(post("/echo")
            .withRequestBody(equalToJson("{\"k\":\"v\"}"))
            .withHeader("Content-Type", equalTo("application/json"))
            .willReturn(aResponse()
                    .withStatus(201)
                    .withBody("created")));

    Result<HttpCallOut> result = execute(HttpCallIn.postJson(baseUrl() + "/echo", "{\"k\":\"v\"}"));

    assertThat(result.isSuccess())
            .as("result cause: %s", result.cause())
            .isTrue();
    assertThat(result.value().status()).isEqualTo(201);
    assertThat(result.value().bodyOrEmpty()).isEqualTo("created");
  }

  @Test
  void customHeadersAndRequestIdAreForwarded() {
    wireMock.stubFor(get("/headers")
            .withHeader("X-Request-Id", equalTo("req-123"))
            .willReturn(aResponse()
                    .withStatus(204)));

    Result<HttpCallOut> result = execute(new HttpCallIn(
            baseUrl() + "/headers", "GET",
            Map.of("X-Request-Id", "req-123"), null, null, null, null));

    assertThat(result.isSuccess())
            .as("result cause: %s", result.cause())
            .isTrue();
    assertThat(result.value().status()).isEqualTo(204);
  }

  @Test
  void mdcRequestIdIsForwardedWhenNoCustomHeaderIsSet() {
    wireMock.stubFor(get("/mdc")
            .withHeader("X-Request-Id", equalTo("mdc-req-1"))
            .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("ok")));

    MDC.put("rid", "mdc-req-1");
    try {
      Result<HttpCallOut> result = execute(HttpCallIn.get(baseUrl() + "/mdc"));
      assertThat(result.isSuccess())
              .as("result cause: %s", result.cause())
              .isTrue();
      assertThat(result.value().status()).isEqualTo(200);
    } finally {
      MDC.clear();
    }
  }

  @Test
  void timeoutFailsInsteadOfHanging() {
    wireMock.stubFor(get("/slow")
            .willReturn(aResponse()
                    .withFixedDelay(2_000)
                    .withStatus(200)));

    long start = System.nanoTime();
    Result<HttpCallOut> result = execute(new HttpCallIn(
            baseUrl() + "/slow", "GET", null, null, 200L, null, null));
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(result.isSuccess())
            .as("expected failure on timeout but got: %s", result.value())
            .isFalse();
    assertThat(result.cause())
            .isInstanceOfAny(HttpCallTransportException.class, RuntimeException.class);
    assertThat(elapsedMs)
            .as("timeout should kick in well before the 2s server delay")
            .isLessThan(1_500);
  }

  @Test
  void redirectIsFollowedToCompletionWhenFollowRedirectsIsAlways() {
    wireMock.stubFor(get("/redirect-always")
            .willReturn(temporaryRedirect("/target-always")));
    wireMock.stubFor(get("/target-always")
            .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("arrived")));

    Result<HttpCallOut> result = execute(new HttpCallIn(
            baseUrl() + "/redirect-always", "GET",
            null, null, null, HttpCallIn.RedirectPolicy.ALWAYS, null));

    assertThat(result.isSuccess())
            .as("result cause: %s", result.cause())
            .isTrue();
    assertThat(result.value().status()).isEqualTo(200);
    assertThat(result.value().bodyOrEmpty()).isEqualTo("arrived");
    wireMock.verify(1, getRequestedFor(urlEqualTo("/redirect-always")));
    wireMock.verify(1, getRequestedFor(urlEqualTo("/target-always")));
  }

  @Test
  void redirectIsFollowedToCompletionWhenFollowRedirectsIsNormal() {
    wireMock.stubFor(get("/redirect-normal")
            .willReturn(temporaryRedirect("/target-normal")));
    wireMock.stubFor(get("/target-normal")
            .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("arrived-normal")));

    Result<HttpCallOut> result = execute(new HttpCallIn(
            baseUrl() + "/redirect-normal", "GET",
            null, null, null, HttpCallIn.RedirectPolicy.NORMAL, null));

    assertThat(result.isSuccess())
            .as("result cause: %s", result.cause())
            .isTrue();
    assertThat(result.value().status()).isEqualTo(200);
    assertThat(result.value().bodyOrEmpty()).isEqualTo("arrived-normal");
    wireMock.verify(1, getRequestedFor(urlEqualTo("/redirect-normal")));
    wireMock.verify(1, getRequestedFor(urlEqualTo("/target-normal")));
  }

  @Test
  void redirectIsNotFollowedWhenFollowRedirectsIsNever() {
    wireMock.stubFor(get("/redirect-never")
            .willReturn(temporaryRedirect("/target-never")));
    wireMock.stubFor(get("/target-never")
            .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("never-arrives")));

    Result<HttpCallOut> result = execute(new HttpCallIn(
            baseUrl() + "/redirect-never", "GET",
            null, null, null, HttpCallIn.RedirectPolicy.NEVER, null));

    assertThat(result.isSuccess())
            .as("redirect should not be followed when policy is NEVER")
            .isFalse();
    assertThat(result.cause())
            .isInstanceOf(HttpCallFailure.class);
    HttpCallFailure failure = (HttpCallFailure) result.cause();
    assertThat(failure.status()).isEqualTo(302);
    wireMock.verify(1, getRequestedFor(urlEqualTo("/redirect-never")));
    wireMock.verify(0, getRequestedFor(urlEqualTo("/target-never")));
  }

  @Test
  void redirectIsNotFollowedWhenFollowRedirectsIsOmitted() {
    wireMock.stubFor(get("/redirect-default")
            .willReturn(temporaryRedirect("/target-default")));
    wireMock.stubFor(get("/target-default")
            .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("default-never-arrives")));

    // followRedirects left null -> HttpCallIn.effectiveRedirects() defaults to NEVER,
    // mirroring the JDK default for the injected shared client.
    Result<HttpCallOut> result = execute(new HttpCallIn(
            baseUrl() + "/redirect-default", "GET",
            null, null, null, null, null));

    assertThat(result.isSuccess())
            .as("redirect should not be followed when followRedirects is omitted (default NEVER)")
            .isFalse();
    assertThat(result.cause())
            .isInstanceOf(HttpCallFailure.class);
    HttpCallFailure failure = (HttpCallFailure) result.cause();
    assertThat(failure.status()).isEqualTo(302);
    wireMock.verify(1, getRequestedFor(urlEqualTo("/redirect-default")));
    wireMock.verify(0, getRequestedFor(urlEqualTo("/target-default")));
  }

  @Test
  void blankUrlProducesFailure() {
    Result<HttpCallOut> result = execute(new HttpCallIn("", "GET", null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause())
            .isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause().getMessage()).contains("url");
  }

  @Test
  void missingUrlProducesFailure() {
    Result<HttpCallOut> result = execute(new HttpCallIn(null, "GET", null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void httpsPublicHostPassesValidation() {
    // example.com is RFC 2606 documentation space; if DNS is unavailable the validator skips
    // the best-effort address check, so this assertion holds either way.
    assertThatCode(() -> OutboundUrlValidator.validate("https://example.com/", secureProperties()))
            .doesNotThrowAnyException();
  }

  @Test
  void loopbackAddressBlockedByDefault() {
    Result<HttpCallOut> result = execute(helperWith(secureProperties()),
            HttpCallIn.get("http://127.0.0.1:" + wireMock.port() + "/metadata"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause().getMessage()).contains("loopback");
    wireMock.verify(0, getRequestedFor(urlEqualTo("/metadata")));
  }

  @Test
  void linkLocalMetadataAddressBlockedByDefault() {
    Result<HttpCallOut> result = execute(helperWith(secureProperties()),
            HttpCallIn.get("http://169.254.169.254/latest/meta-data"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause().getMessage()).contains("link-local");
  }

  @Test
  void ipv6LoopbackAddressBlockedByDefault() {
    Result<HttpCallOut> result = execute(helperWith(secureProperties()),
            HttpCallIn.get("http://[::1]:" + wireMock.port() + "/metadata"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause().getMessage()).contains("loopback");
  }

  @Test
  void siteLocalAddressBlockedByDefault() {
    Result<HttpCallOut> result = execute(helperWith(secureProperties()),
            HttpCallIn.get("http://10.0.0.5/internal"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause().getMessage()).contains("site-local");
  }

  @Test
  void privateAddressesPassWhenBlockFlagIsOff() {
    wireMock.stubFor(get("/private-ok")
            .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("ok")));

    // End-to-end against 127.0.0.1 with the legacy permissive helper (flag off).
    Result<HttpCallOut> result = execute(
            HttpCallIn.get("http://127.0.0.1:" + wireMock.port() + "/private-ok"));
    assertThat(result.isSuccess())
            .as("result cause: %s", result.cause())
            .isTrue();
    assertThat(result.value().bodyOrEmpty()).isEqualTo("ok");

    // Validator-level for the categories with no local listener.
    var permissive = HttpCallProperties.permissive();
    assertThatCode(() -> OutboundUrlValidator.validate("http://169.254.169.254/latest", permissive))
            .doesNotThrowAnyException();
    assertThatCode(() -> OutboundUrlValidator.validate("http://[::1]/x", permissive))
            .doesNotThrowAnyException();
    assertThatCode(() -> OutboundUrlValidator.validate("http://10.0.0.5/x", permissive))
            .doesNotThrowAnyException();
  }

  @Test
  void disallowedSchemeIsRejected() {
    Result<HttpCallOut> result = execute(helperWith(secureProperties()),
            HttpCallIn.get("ftp://example.com/file"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause().getMessage()).contains("'ftp'");
  }

  @Test
  void allowedHostsMatchPermitsRequest() {
    wireMock.stubFor(get("/hosts-ok")
            .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("hosts-ok")));
    var helper = helperWith(
            new HttpCallProperties(List.of("https", "http"), false, List.of("localhost")));

    Result<HttpCallOut> result = execute(helper, HttpCallIn.get(baseUrl() + "/hosts-ok"));

    assertThat(result.isSuccess())
            .as("result cause: %s", result.cause())
            .isTrue();
    assertThat(result.value().bodyOrEmpty()).isEqualTo("hosts-ok");
  }

  @Test
  void allowedHostsSupportsWildcardSuffixMatch() {
    var properties = new HttpCallProperties(null, false, List.of("*.example.com"));

    assertThatCode(() -> OutboundUrlValidator.validate("https://api.example.com/x", properties))
            .doesNotThrowAnyException();
    assertThatCode(() -> OutboundUrlValidator.validate("https://evil-example.com/x", properties))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("host is not allowed");
  }

  @Test
  void allowedHostsNoMatchIsRejected() {
    var helper = helperWith(new HttpCallProperties(
            List.of("https", "http"), false, List.of("partner.example.com")));

    Result<HttpCallOut> result = execute(helper, HttpCallIn.get(baseUrl() + "/not-allowed"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause().getMessage()).contains("host is not allowed");
    wireMock.verify(0, getRequestedFor(urlEqualTo("/not-allowed")));
  }

  @Test
  void userInfoIsNeverEchoedInFailureMessage() {
    Result<HttpCallOut> result = execute(helperWith(secureProperties()),
            HttpCallIn.get("http://user:pass@127.0.0.1:" + wireMock.port() + "/x"));

    assertThat(result.isSuccess()).isFalse();
    String message = result.cause().getMessage();
    assertThat(message).contains("127.0.0.1");
    assertThat(message)
            .doesNotContain("user:pass")
            .doesNotContain("user");
  }

  @Test
  void redirectTargetIsRevalidatedAfterFollow() {
    // blockPrivateAddresses off so the initial request to localhost passes; allowedHosts only
    // admits "localhost", so the redirect to 127.0.0.1 must be rejected post-hoc.
    var helper = helperWith(
            new HttpCallProperties(List.of("https", "http"), false, List.of("localhost")));
    wireMock.stubFor(get("/redirect-blocked")
            .willReturn(
                    temporaryRedirect("http://127.0.0.1:" + wireMock.port() + "/target-blocked")));
    wireMock.stubFor(get("/target-blocked")
            .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("should not be returned")));

    Result<HttpCallOut> result = execute(helper, new HttpCallIn(
            baseUrl() + "/redirect-blocked", "GET",
            null, null, null, HttpCallIn.RedirectPolicy.ALWAYS, null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause().getMessage())
            .contains("redirect")
            .contains("127.0.0.1");
    // Detection, not prevention: the JDK client followed the redirect before we could reject it.
    wireMock.verify(1, getRequestedFor(urlEqualTo("/target-blocked")));
  }
}
