package cbs.nova.starter.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit specs for {@link GenericJsonRuleSink} (the {@code slack}/{@code pagerduty} adapters). The
 * sink POSTs a small {@code {"text": ...}} JSON body to the action URL with a single attempt, no
 * signing. HTTP-level errors and transport exceptions are converted into
 * {@link FiringOutcome#failure} — nothing is thrown. A local WireMock server stands in for the chat
 * endpoint (same approach as the engine integration test).
 */
class GenericJsonRuleSinkTest {

  private static final String CHAT_PATH = "/chat";

  private WireMockServer wireMock;

  private HttpClient httpClient;

  private GenericJsonRuleSink slackSink;

  private GenericJsonRuleSink pagerDutySink;

  @BeforeEach
  void setUp() {
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();
    httpClient = HttpClient.newHttpClient();
    slackSink = new GenericJsonRuleSink("slack", httpClient, new ObjectMapper(),
            Duration.ofSeconds(5));
    pagerDutySink = new GenericJsonRuleSink("pagerduty", httpClient, new ObjectMapper(),
            Duration.ofSeconds(5));
  }

  @AfterEach
  void tearDown() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  @Test
  void sinkTypeIsWhateverConstructorReceived() {
    assertThat(slackSink.sinkType()).isEqualTo("slack");
    assertThat(pagerDutySink.sinkType()).isEqualTo("pagerduty");
  }

  @Test
  void deliverPostsTextJsonBodyWithJsonContentType() {
    wireMock.stubFor(post(CHAT_PATH).willReturn(
            com.github.tomakehurst.wiremock.client.WireMock.ok()));
    NotificationRuleEntity rule = rule("loan-failures");
    NotificationActionDto action = new NotificationActionDto("slack", baseUrl() + CHAT_PATH, null,
            null);

    FiringOutcome outcome = slackSink.deliver(rule, action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("success");
    assertThat(outcome.detail()).isEqualTo("http 200");
    assertThat(outcome.durationMs()).isGreaterThanOrEqualTo(0L);
    wireMock.verify(postRequestedFor(urlEqualTo(CHAT_PATH))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalTo(
                    "{\"text\":\"Rule 'loan-failures': RunFailed on run/run-1\"}")));
  }

  @Test
  void threeHundredLevelStatusIsStillSuccess() {
    wireMock.stubFor(post(CHAT_PATH).willReturn(
            com.github.tomakehurst.wiremock.client.WireMock.status(302)));
    NotificationActionDto action = new NotificationActionDto("slack", baseUrl() + CHAT_PATH, null,
            null);

    FiringOutcome outcome = slackSink.deliver(rule("r"), action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("success");
    assertThat(outcome.detail()).isEqualTo("http 302");
  }

  @Test
  void non2xxResponseBecomesFailureWithHttpDetail() {
    wireMock.stubFor(post(CHAT_PATH).willReturn(serverError()));
    NotificationActionDto action = new NotificationActionDto("pagerduty", baseUrl() + CHAT_PATH,
            null, null);

    FiringOutcome outcome = pagerDutySink.deliver(rule("r"), action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("failure");
    assertThat(outcome.detail()).isEqualTo("http 500");
  }

  @Test
  void transportFailureBecomesFailureOutcomeInsteadOfThrowing() {
    String deadUrl = baseUrl() + CHAT_PATH;
    wireMock.stop();
    NotificationActionDto action = new NotificationActionDto("slack", deadUrl, null, null);

    assertThatCode(() -> slackSink.deliver(rule("r"), action, runFailed()))
            .doesNotThrowAnyException();

    FiringOutcome outcome = slackSink.deliver(rule("r"), action, runFailed());
    assertThat(outcome.outcome()).isEqualTo("failure");
    // java.net.http ConnectException carries a null message on this JDK — pinned as-is.
    assertThat(outcome.detail()).isNull();
  }

  private String baseUrl() {
    return "http://localhost:" + wireMock.port();
  }

  private static NotificationRuleEntity rule(String name) {
    Instant now = Instant.now();
    return new NotificationRuleEntity(1L, name, true, "RunFailed", null, null, null, null, "[]", 0,
            "default", now, now);
  }

  private static DomainEvent.RunFailed runFailed() {
    Instant now = Instant.now();
    return new DomainEvent.RunFailed("run-1", "LoanOrigination", DslRunStatus.FAILED, "boom", now,
            now, null, null);
  }
}
