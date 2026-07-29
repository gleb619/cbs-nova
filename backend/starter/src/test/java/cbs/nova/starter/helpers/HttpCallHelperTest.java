package cbs.nova.starter.helpers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helpers.HttpCallHelper.HttpCallFailure;
import cbs.nova.starter.helpers.HttpCallHelper.HttpCallTransportException;
import cbs.nova.starter.helpers.model.HttpCallIn;
import cbs.nova.starter.helpers.model.HttpCallOut;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    helper = new HttpCallHelper(HttpClient.newHttpClient());
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
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
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
            Map.of("X-Request-Id", "req-123"), null, null, null));

    assertThat(result.isSuccess())
            .as("result cause: %s", result.cause())
            .isTrue();
    assertThat(result.value().status()).isEqualTo(204);
  }

  @Test
  void timeoutFailsInsteadOfHanging() {
    wireMock.stubFor(get("/slow")
            .willReturn(aResponse()
                    .withFixedDelay(2_000)
                    .withStatus(200)));

    long start = System.nanoTime();
    Result<HttpCallOut> result = execute(new HttpCallIn(
            baseUrl() + "/slow", "GET", null, null, 200L, null));
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
  void blankUrlProducesFailure() {
    Result<HttpCallOut> result = execute(new HttpCallIn("", "GET", null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause())
            .isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause().getMessage()).contains("url");
  }

  @Test
  void missingUrlProducesFailure() {
    Result<HttpCallOut> result = execute(new HttpCallIn(null, "GET", null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause())
            .isInstanceOf(IllegalArgumentException.class);
  }
}
