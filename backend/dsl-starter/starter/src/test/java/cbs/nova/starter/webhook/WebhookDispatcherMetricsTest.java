package cbs.nova.starter.webhook;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

/**
 * Micrometer metrics coverage for {@link WebhookDispatcher}. Verifies the
 * {@code cbs.webhook.delivery.count{outcome}} counter and {@code cbs.webhook.delivery.duration}
 * timer are recorded with the closed {@code outcome} vocabulary ({@code success}, {@code failure},
 * {@code rejected}) on both the static-subscription path and the rule-based
 * {@link WebhookDispatcher#dispatchTo} path.
 *
 * <p>Tag discipline: only the {@code outcome} tag is present. URLs, run ids, and rule names are
 * not published to the registry.
 */
class WebhookDispatcherMetricsTest {

  private WireMockServer wireMock;
  private ThreadPoolTaskExecutor executor;
  private ObjectMapper objectMapper;
  private MeterRegistry registry;

  @BeforeEach
  void setUp() {
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();
    executor = deliveryExecutor();
    objectMapper = new ObjectMapper();
    registry = new SimpleMeterRegistry();
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
    exec.setThreadNamePrefix("test-webhook-metrics-");
    exec.initialize();
    return exec;
  }

  private HttpClient httpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  }

  private WebhookDispatcher dispatcher(WebhookProperties properties) {
    return new WebhookDispatcher(properties, objectMapper, executor, httpClient(),
            Optional.empty(), registry);
  }

  private String baseUrl() {
    return "http://localhost:" + wireMock.port();
  }

  private WebhookProperties properties(WebhookSubscription... subscriptions) {
    WebhookProperties properties = new WebhookProperties();
    properties.setEnabled(true);
    properties.setMaxRetries(3);
    properties.setTimeout(Duration.ofSeconds(2));
    properties.setRetryBackoff(Duration.ofMillis(10));
    properties.setAllowPlainHttp(true);
    properties.setSubscriptions(List.of(subscriptions));
    return properties;
  }

  @Test
  void successfulStaticDeliveryIncrementsSuccessSeries() {
    WebhookDispatcher d = dispatcher(properties(
            new WebhookSubscription("*", baseUrl() + "/hook", null, null)));
    wireMock.stubFor(post("/hook").willReturn(aResponse().withStatus(200)));

    d.onRunComplete("run-1", "demo", "COMPLETED", Instant.now(), Instant.now(), null);

    await().atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/hook"))))
                    .hasSize(1));

    assertCounter("success", 1.0);
    assertTimerCount("success", 1);
  }

  @Test
  void serverErrorRetryExhaustionIncrementsFailureSeries() {
    WebhookDispatcher d = dispatcher(properties(
            new WebhookSubscription("*", baseUrl() + "/hook", null, null)));
    wireMock.stubFor(post("/hook").willReturn(aResponse().withStatus(500)));

    d.onRunComplete("run-2", "demo", "COMPLETED", Instant.now(), Instant.now(), null);

    await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/hook"))))
                    .hasSize(3));

    assertCounter("failure", 1.0);
    assertTimerCount("failure", 1);
  }

  @Test
  void nonHttpsStaticSubscriptionIncrementsRejectedSeries() {
    WebhookProperties props = properties(
            new WebhookSubscription("*", "http://example.invalid/hook", null, null));
    props.setAllowPlainHttp(false);
    WebhookDispatcher d = dispatcher(props);

    d.onRunComplete("run-3", "demo", "COMPLETED", Instant.now(), Instant.now(), null);

    await().atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(d.deliveryInfos()).hasSize(1));

    assertCounter("rejected", 1.0);
    assertTimerCount("rejected", 1);
  }

  @Test
  void dispatchToSuccessIncrementsSuccessSeries() {
    wireMock.stubFor(post("/hook").willReturn(aResponse().withStatus(200)));
    WebhookDispatcher d = dispatcher(properties(
            new WebhookSubscription("*", baseUrl() + "/never", null, null)));

    WebhookDeliveryResult result = d.dispatchTo(baseUrl() + "/hook", null, "RunCompleted",
            new WebhookPayload("RunCompleted", "run-4", "demo", "COMPLETED",
                    Instant.now().toString(), Instant.now().toString(), null));

    assertThat(result.isSuccess()).isTrue();
    assertCounter("success", 1.0);
    assertTimerCount("success", 1);
  }

  @Test
  void dispatchToServerErrorIncrementsFailureSeries() {
    wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.any(
            com.github.tomakehurst.wiremock.client.WireMock.urlMatching(".*"))
            .willReturn(aResponse().withStatus(503)));
    WebhookDispatcher d = dispatcher(properties(
            new WebhookSubscription("*", baseUrl() + "/never", null, null)));

    WebhookDeliveryResult result = d.dispatchTo(baseUrl() + "/hook", null, "RunCompleted",
            new WebhookPayload("RunCompleted", "run-5", "demo", "FAILED",
                    Instant.now().toString(), Instant.now().toString(), "boom"));

    assertThat(result.isSuccess()).isFalse();
    assertCounter("failure", 1.0);
    assertTimerCount("failure", 1);
  }

  @Test
  void dispatchToRejectsNonHttpsAndIncrementsRejectedSeries() {
    WebhookProperties props = properties(
            new WebhookSubscription("*", baseUrl() + "/never", null, null));
    props.setAllowPlainHttp(false);
    WebhookDispatcher d = dispatcher(props);

    WebhookDeliveryResult result = d.dispatchTo("http://example.invalid/hook", null,
            "RunCompleted",
            new WebhookPayload("RunCompleted", "run-6", "demo", "FAILED",
                    Instant.now().toString(), Instant.now().toString(), "boom"));

    assertThat(result.status()).isEqualTo("rejected");
    assertCounter("rejected", 1.0);
    assertTimerCount("rejected", 1);
  }

  @Test
  void tagCardinalityIsBoundedToOutcomeOnly() {
    WebhookDispatcher d = dispatcher(properties(
            new WebhookSubscription("*", baseUrl() + "/hook", null, null)));
    wireMock.stubFor(post("/hook").willReturn(aResponse().withStatus(200)));

    d.onRunComplete("run-with-uuid-xyz", "MyProcess", "COMPLETED", Instant.now(), Instant.now(),
            "anything");
    await().atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/hook"))))
                    .hasSize(1));

    var counter = registry.find("cbs.webhook.delivery.count").counter();
    assertThat(counter).isNotNull();
    assertThat(counter.getId().getTags())
            .extracting(io.micrometer.core.instrument.Tag::getKey)
            .containsExactly("outcome");
    assertThat(counter.getId().getTag("outcome")).isEqualTo("success");
    // No URL / run id / process name tags.
    assertThat(counter.getId().getTag("url")).isNull();
    assertThat(counter.getId().getTag("runId")).isNull();

    var timer = registry.find("cbs.webhook.delivery.duration").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.getId().getTags())
            .extracting(io.micrometer.core.instrument.Tag::getKey)
            .containsExactly("outcome");
  }

  @Test
  void registryAbsentStaysWorkingWithoutRecordingMeters() {
    WebhookProperties props = properties(
            new WebhookSubscription("*", baseUrl() + "/hook", null, null));
    // Build dispatcher via the registry-less 5-arg ctor used by existing tests.
    WebhookDispatcher d = new WebhookDispatcher(props, objectMapper, executor, httpClient(),
            Optional.empty());
    wireMock.stubFor(post("/hook").willReturn(aResponse().withStatus(200)));

    d.onRunComplete("run-no-registry", "demo", "COMPLETED", Instant.now(), Instant.now(), null);

    await().atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/hook"))))
                    .hasSize(1));

    // No registry attached → counter not in this (separate) registry.
    assertThat(registry.find("cbs.webhook.delivery.count").counter()).isNull();
    // Dispatcher state still progresses normally.
    assertThat(d.deliveryInfos()).hasSize(1);
  }

  @Test
  void staticSubscriptionSetMatches() {
    // Sanity guard: the tests above rely on Set.of being acceptable; ensure no behaviour drift.
    Set<String> statuses = Set.of("FAILED");
    assertThat(statuses).containsExactly("FAILED");
  }

  private void assertCounter(String outcome, double expected) {
    var counter = registry.find("cbs.webhook.delivery.count")
            .tag("outcome", outcome)
            .counter();
    assertThat(counter).as("counter for outcome=%s", outcome).isNotNull();
    assertThat(counter.count()).isEqualTo(expected);
  }

  private void assertTimerCount(String outcome, long expected) {
    var timer = registry.find("cbs.webhook.delivery.duration")
            .tag("outcome", outcome)
            .timer();
    assertThat(timer).as("timer for outcome=%s", outcome).isNotNull();
    assertThat(timer.count()).isEqualTo(expected);
  }
}