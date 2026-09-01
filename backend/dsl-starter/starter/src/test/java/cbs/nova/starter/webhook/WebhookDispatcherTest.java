package cbs.nova.starter.webhook;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

class WebhookDispatcherTest {

  private static final String SIGNATURE_HEADER = "X-Cbs-Signature";

  private static final String EVENT_HEADER = "X-Cbs-Event";

  private WireMockServer wireMock;

  private ThreadPoolTaskExecutor executor;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();
    executor = deliveryExecutor();
    objectMapper = new ObjectMapper();
  }

  @AfterEach
  void tearDown() {
    if (wireMock != null) {
      wireMock.stop();
    }
    if (executor != null) {
      executor.shutdown();
    }
  }

  private ThreadPoolTaskExecutor deliveryExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(1);
    exec.setMaxPoolSize(2);
    exec.setQueueCapacity(10);
    exec.setThreadNamePrefix("test-webhook-");
    exec.initialize();
    return exec;
  }

  private WebhookDispatcher newDispatcher(WebhookProperties properties) {
    return new WebhookDispatcher(properties, objectMapper, executor);
  }

  private String baseUrl() {
    return "http://localhost:" + wireMock.port();
  }

  @Test
  void signatureHeaderContainsHmacSha256OfBody() throws Exception {
    String secret = "my-secret";
    WebhookProperties properties = enabledProperties(
            new WebhookSubscription("*", baseUrl() + "/hook", secret, null));
    WebhookDispatcher dispatcher = newDispatcher(properties);

    wireMock.stubFor(post("/hook").willReturn(ok()));

    Instant startedAt = Instant.parse("2024-01-01T00:00:00Z");
    Instant finishedAt = Instant.parse("2024-01-01T00:00:01Z");
    dispatcher.onRunComplete("run-1", "demo", "COMPLETED", startedAt, finishedAt, null);

    await().atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/hook"))))
                    .hasSize(1));

    byte[] body = expectedBody("run-1", "demo", "COMPLETED", startedAt.toString(),
            finishedAt.toString(), null);
    String expected = hmacHex(secret, body);

    wireMock.verify(postRequestedFor(urlEqualTo("/hook"))
            .withHeader(SIGNATURE_HEADER, equalTo("sha256=" + expected))
            .withHeader(EVENT_HEADER, equalTo("run.completed")));
  }

  @Test
  void statusFilterSkipsNonMatchingStatus() {
    WebhookProperties properties = enabledProperties(
            new WebhookSubscription("*", baseUrl() + "/hook", null, Set.of("FAILED")));
    WebhookDispatcher dispatcher = newDispatcher(properties);

    wireMock.stubFor(post("/hook").willReturn(ok()));

    dispatcher.onRunComplete("run-2", "demo", "COMPLETED", Instant.now(), Instant.now(), null);

    await().pollDelay(Duration.ofMillis(200)).atMost(Duration.ofSeconds(1))
            .untilAsserted(() -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/hook"))))
                    .isEmpty());
  }

  @Test
  void definitionPatternRespectsGlob() {
    WebhookProperties properties = enabledProperties(
            new WebhookSubscription("orders-*", baseUrl() + "/orders", null, null),
            new WebhookSubscription("billing", baseUrl() + "/billing", null, null));
    WebhookDispatcher dispatcher = newDispatcher(properties);

    wireMock.stubFor(post("/orders").willReturn(ok()));
    wireMock.stubFor(post("/billing").willReturn(ok()));

    dispatcher.onRunComplete("run-3", "orders-sync", "COMPLETED", Instant.now(), Instant.now(),
            null);

    await().atMost(Duration.ofSeconds(3))
            .untilAsserted(
                    () -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/orders"))))
                            .hasSize(1));

    assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/billing")))).isEmpty();
  }

  @Test
  void retrySucceedsOnThirdAttempt() {
    WebhookProperties properties = enabledProperties(
            new WebhookSubscription("*", baseUrl() + "/hook", null, null));
    WebhookDispatcher dispatcher = newDispatcher(properties);

    wireMock.stubFor(post("/hook")
            .inScenario("retry")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("second"));
    wireMock.stubFor(post("/hook")
            .inScenario("retry")
            .whenScenarioStateIs("second")
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("third"));
    wireMock.stubFor(post("/hook")
            .inScenario("retry")
            .whenScenarioStateIs("third")
            .willReturn(aResponse().withStatus(200)));

    dispatcher.onRunComplete("run-4", "demo", "COMPLETED", Instant.now(), Instant.now(), null);

    await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/hook"))))
                    .hasSize(3));

    WebhookDeliveryInfo outcome = singleOutcome(dispatcher);
    assertThat(outcome.lastStatus()).isEqualTo("200");
    assertThat(outcome.lastAttempts()).isEqualTo(3);
  }

  @Test
  void retryExhaustionRecordsFailure() {
    WebhookProperties properties = enabledProperties(
            new WebhookSubscription("*", baseUrl() + "/hook", null, null));
    WebhookDispatcher dispatcher = newDispatcher(properties);

    wireMock.stubFor(post("/hook").willReturn(aResponse().withStatus(500)));

    dispatcher.onRunComplete("run-5", "demo", "COMPLETED", Instant.now(), Instant.now(), null);

    await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/hook"))))
                    .hasSize(3));

    WebhookDeliveryInfo outcome = singleOutcome(dispatcher);
    assertThat(outcome.lastStatus()).isEqualTo("500");
    assertThat(outcome.lastAttempts()).isEqualTo(3);
  }

  @Test
  void disabledOrEmptySubscriptionsCauseNoDelivery() {
    WebhookProperties disabled = enabledProperties(
            new WebhookSubscription("*", baseUrl() + "/hook", null, null));
    disabled.setEnabled(false);
    WebhookDispatcher disabledDispatcher = newDispatcher(disabled);

    wireMock.stubFor(post("/hook").willReturn(ok()));
    disabledDispatcher.onRunComplete("run-6", "demo", "COMPLETED", Instant.now(), Instant.now(),
            null);

    WebhookProperties empty = new WebhookProperties();
    empty.setEnabled(true);
    WebhookDispatcher emptyDispatcher = newDispatcher(empty);

    emptyDispatcher.onRunComplete("run-7", "demo", "COMPLETED", Instant.now(), Instant.now(), null);

    await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1))
            .untilAsserted(() -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/hook"))))
                    .isEmpty());
  }

  @Test
  void plainHttpIsRejectedWhenNotAllowed() {
    WebhookProperties properties = enabledProperties(
            new WebhookSubscription("*", baseUrl() + "/hook", null, null));
    properties.setAllowPlainHttp(false);
    WebhookDispatcher dispatcher = newDispatcher(properties);

    wireMock.stubFor(post("/hook").willReturn(ok()));

    dispatcher.onRunComplete("run-8", "demo", "COMPLETED", Instant.now(), Instant.now(), null);

    await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1))
            .untilAsserted(() -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/hook"))))
                    .isEmpty());

    WebhookDeliveryInfo outcome = singleOutcome(dispatcher);
    assertThat(outcome.lastStatus()).isEqualTo("rejected");
  }

  @Test
  void slowCallbackDoesNotBlockCaller() {
    WebhookProperties properties = enabledProperties(
            new WebhookSubscription("*", baseUrl() + "/hook", null, null));
    WebhookDispatcher dispatcher = newDispatcher(properties);

    wireMock.stubFor(post("/hook")
            .willReturn(aResponse().withStatus(200).withFixedDelay(800)));

    long startNs = System.nanoTime();
    dispatcher.onRunComplete("run-9", "demo", "COMPLETED", Instant.now(), Instant.now(), null);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - startNs);

    assertThat(elapsed).isLessThan(Duration.ofMillis(100));

    await().atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/hook"))))
                    .hasSize(1));
  }

  private WebhookProperties enabledProperties(WebhookSubscription... subscriptions) {
    WebhookProperties properties = new WebhookProperties();
    properties.setEnabled(true);
    properties.setMaxRetries(3);
    properties.setTimeout(Duration.ofSeconds(2));
    properties.setRetryBackoff(Duration.ofMillis(10));
    properties.setAllowPlainHttp(true);
    properties.setSubscriptions(List.of(subscriptions));
    return properties;
  }

  private WebhookDeliveryInfo singleOutcome(WebhookDispatcher dispatcher) {
    return await().atMost(Duration.ofSeconds(3))
            .until(dispatcher.deliveryInfos()::iterator, i -> i.hasNext())
            .next();
  }

  private byte[] expectedBody(String runId, String definition, String status, String startedAt,
          String finishedAt, String error) throws IOException {
    WebhookPayload payload = new WebhookPayload("run.completed", runId, definition, status,
            startedAt,
            finishedAt, error);
    return objectMapper.writeValueAsBytes(payload);
  }

  private String hmacHex(String secret, byte[] body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    mac.init(key);
    byte[] digest = mac.doFinal(body);
    StringBuilder hex = new StringBuilder(digest.length * 2);
    for (byte b : digest) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }
}
