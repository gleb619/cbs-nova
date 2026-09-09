package cbs.nova.starter.webhook;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = cbs.nova.starter.WebhookTestApplication.class)
@Sql(scripts = {"classpath:db/migration/h2/V1__init.sql",
    "classpath:db/migration/h2/V3__dsl_webhook_deliveries.sql",
    "classpath:sql/truncate-dsl-webhook-deliveries.sql"})
@TestPropertySource(properties = {
    "csb.dsl.worker.enabled=false"
})
class WebhookDispatcherPersistenceTest {

  private WireMockServer wireMock;

  private ThreadPoolTaskExecutor executor;

  private ObjectMapper objectMapper;

  @Autowired
  private WebhookDeliveryRecordRepository repository;

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
    exec.setThreadNamePrefix("test-webhook-persist-");
    exec.initialize();
    return exec;
  }

  private WebhookDispatcher newDispatcher(WebhookProperties properties) {
    return new WebhookDispatcher(properties, objectMapper, executor, Optional.of(repository));
  }

  private String baseUrl() {
    return "http://localhost:" + wireMock.port();
  }

  @Test
  void failedDeliveryPersistsOutcomeWithAttemptsAndLastError() {
    WebhookProperties properties = enabledProperties(
            new WebhookSubscription("*", baseUrl() + "/hook", null, null));
    WebhookDispatcher dispatcher = newDispatcher(properties);

    wireMock.stubFor(post("/hook").willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

    dispatcher.onRunComplete("run-persist", "demo", "COMPLETED", Instant.now(), Instant.now(),
            null);

    await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/hook"))))
                    .hasSize(2));

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
      WebhookDeliverySearchResult result = repository.search(null, 0, 10);
      assertThat(result.total()).isEqualTo(1);
      WebhookDeliveryRecord row = result.items().get(0);
      assertThat(row.status()).isEqualTo("failed");
      assertThat(row.attempts()).isEqualTo(2);
      assertThat(row.lastError()).isNotBlank();
    });
  }

  private WebhookProperties enabledProperties(WebhookSubscription... subscriptions) {
    WebhookProperties properties = new WebhookProperties();
    properties.setEnabled(true);
    properties.setMaxRetries(2);
    properties.setTimeout(Duration.ofSeconds(2));
    properties.setRetryBackoff(Duration.ofMillis(10));
    properties.setAllowPlainHttp(true);
    properties.setSubscriptions(List.of(subscriptions));
    return properties;
  }

}
