package cbs.nova.starter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import cbs.nova.starter.security.WebhookUrlGuard;
import cbs.nova.starter.webhook.WebhookProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit specs for {@link PagerDutyRuleSink}: real Events API v2 envelope, missing-secret guard,
 * default URL when {@code action.url} is blank, 2xx success, non-2xx failure, and routing-key
 * redaction. Mocks {@link HttpClient} so the test pins the exact wire bytes (routing key,
 * event_action, dedup_key, payload.{summary, source, severity, custom_details}) without standing up
 * an HTTP server.
 */
class PagerDutyRuleSinkTest {

  private HttpClient httpClient;

  private ObjectMapper objectMapper;

  private WebhookProperties properties;

  private PagerDutyRuleSink sink;

  private RecordingInvocation recording;

  @BeforeEach
  void setUp() {
    httpClient = mock(HttpClient.class);
    objectMapper = new ObjectMapper();
    properties = WebhookUrlGuard.permissiveProperties();
    sink = new PagerDutyRuleSink(httpClient, objectMapper, Duration.ofSeconds(5), properties);
    recording = new RecordingInvocation(httpClient);
  }

  @Test
  void sinkTypeIsPagerDuty() {
    assertThat(sink.sinkType()).isEqualTo("pagerduty");
  }

  @Test
  void deliverBuildsEventsV2EnvelopeAndUsesDefaultUrl() throws Exception {
    recording.respondWith(202);
    NotificationActionDto action = new NotificationActionDto("pagerduty", null, "routing-key-1",
            null);

    FiringOutcome outcome = sink.deliver(rule(42L, "loan-failures"), action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("success");
    assertThat(outcome.detail()).isEqualTo("http 202");
    assertThat(recording.lastUri()).isEqualTo(URI.create(PagerDutyRuleSink.DEFAULT_URL));
    Map<String, Object> body = objectMapper.readValue(recording.lastBody(),
            new TypeReference<Map<String, Object>>() {
            });
    assertThat(body)
            .containsEntry("routing_key", "routing-key-1")
            .containsEntry("event_action", "trigger")
            .containsEntry("dedup_key", "42:run:run-1");
    Map<String, Object> payload = (Map<String, Object>) body.get("payload");
    assertThat(payload)
            .containsEntry("summary", "Rule 'loan-failures': RunFailed on run/run-1")
            .containsEntry("source", "cbs-nova")
            .containsEntry("severity", "error");
    Map<String, Object> custom = (Map<String, Object>) payload.get("custom_details");
    assertThat(custom)
            .containsEntry("eventType", "RunFailed")
            .containsEntry("aggregateType", "run")
            .containsEntry("aggregateId", "run-1")
            .containsEntry("correlationId", null);
  }

  @Test
  void severityIsInfoForNonFailedEvents() throws Exception {
    recording.respondWith(202);
    NotificationActionDto action = new NotificationActionDto("pagerduty", null, "rk", null);

    FiringOutcome outcome = sink.deliver(rule(1L, "r"), action, runStarted());

    assertThat(outcome.outcome()).isEqualTo("success");
    Map<String, Object> body = objectMapper.readValue(recording.lastBody(),
            new TypeReference<Map<String, Object>>() {
            });
    Map<String, Object> payload = (Map<String, Object>) body.get("payload");
    assertThat(payload).containsEntry("severity", "info");
  }

  @Test
  void customUrlOverridesDefaultAndIsRespected() {
    recording.respondWith(202);
    NotificationActionDto action = new NotificationActionDto("pagerduty",
            "https://events.pagerduty.example/v2/enqueue", "rk", null);

    FiringOutcome outcome = sink.deliver(rule(1L, "r"), action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("success");
    assertThat(recording.lastUri()).isEqualTo(
            URI.create("https://events.pagerduty.example/v2/enqueue"));
  }

  @Test
  void missingSecretBecomesFailureWithoutHttpCall() throws Exception {
    NotificationActionDto action = new NotificationActionDto("pagerduty", null, null, null);

    FiringOutcome outcome = sink.deliver(rule(1L, "r"), action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("failure");
    assertThat(outcome.detail()).isEqualTo("pagerduty requires routing key (secret)");
    assertThat(outcome.durationMs()).isEqualTo(0L);
    assertThat(recording.callCount()).isEqualTo(0);
  }

  @Test
  void blankSecretBecomesFailureWithoutHttpCall() throws Exception {
    NotificationActionDto action = new NotificationActionDto("pagerduty", null, "   ", null);

    FiringOutcome outcome = sink.deliver(rule(1L, "r"), action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("failure");
    assertThat(outcome.detail()).isEqualTo("pagerduty requires routing key (secret)");
    assertThat(recording.callCount()).isEqualTo(0);
  }

  @Test
  void non2xxResponseBecomesFailureWithHttpDetail() {
    recording.respondWith(400);
    NotificationActionDto action = new NotificationActionDto("pagerduty", null, "rk", null);

    FiringOutcome outcome = sink.deliver(rule(1L, "r"), action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("failure");
    assertThat(outcome.detail()).isEqualTo("http 400");
    assertThat(outcome.detail()).doesNotContain("rk");
  }

  @Test
  void transportFailureBecomesFailureOutcomeInsteadOfThrowing() {
    recording.throwOnNext(new IOException("boom"));
    NotificationActionDto action = new NotificationActionDto("pagerduty", null, "rk", null);

    assertThatCode(() -> sink.deliver(rule(1L, "r"), action, runFailed()))
            .doesNotThrowAnyException();

    FiringOutcome outcome = sink.deliver(rule(1L, "r"), action, runFailed());
    assertThat(outcome.outcome()).isEqualTo("failure");
  }

  @Test
  void routingKeyIsNeverEchoedInFailureDetail() {
    recording.respondWith(500);
    NotificationActionDto action = new NotificationActionDto("pagerduty", null,
            "super-secret-routing-key", null);

    FiringOutcome outcome = sink.deliver(rule(1L, "r"), action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("failure");
    assertThat(outcome.detail()).doesNotContain("super-secret-routing-key");
  }

  @Test
  void unsafeUrlReturnsFailureWithoutCallingHttpClient() {
    properties.setBlockPrivateAddresses(true);
    NotificationActionDto action = new NotificationActionDto("pagerduty",
            "http://169.254.169.254/events", "rk", null);

    FiringOutcome outcome = sink.deliver(rule(1L, "r"), action, runFailed());

    assertThat(outcome.outcome()).isEqualTo("failure");
    assertThat(outcome.detail()).contains("notification sink url");
    assertThat(recording.callCount()).isEqualTo(0);
  }

  private static NotificationRuleEntity rule(long id, String name) {
    Instant now = Instant.now();
    return new NotificationRuleEntity(id, name, true, "RunFailed", null, null, null, null, "[]", 0,
            "default", now, now);
  }

  private static DomainEvent.RunFailed runFailed() {
    Instant now = Instant.now();
    return new DomainEvent.RunFailed("run-1", "LoanOrigination", DslRunStatus.FAILED, "boom", now,
            now, null, null);
  }

  private static DomainEvent.RunStarted runStarted() {
    return new DomainEvent.RunStarted("run-2", "LoanOrigination", null, Instant.now(), null);
  }

  /**
   * Wires a stub response into the mocked {@link HttpClient}: captures the last request URI and
   * body, and lets the next call either return a fixed HTTP status or throw an {@link IOException}.
   * Drains the {@link HttpRequest.BodyPublisher} via a {@link Flow.Subscriber} so the test pins the
   * exact bytes the sink emitted.
   */
  private static final class RecordingInvocation {

    private final HttpClient mock;

    private URI lastUri;
    private byte[] lastBody = new byte[0];
    private int calls;
    private int nextStatus = 202;
    private IOException nextException;
    private HttpResponse<Void> nextResponse;

    RecordingInvocation(HttpClient mock) {
      this.mock = mock;
      try {
        doAnswer(invocation -> {
          try {
            return handle(invocation);
          } catch (IOException ex) {
            throw new RuntimeException(ex);
          }
        }).when(mock).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }

    private HttpResponse<Void> handle(InvocationOnMock invocation)
            throws IOException {
      calls++;
      HttpRequest request = invocation.getArgument(0);
      lastUri = request.uri();
      lastBody = drain(request.bodyPublisher().orElse(null));
      if (nextException != null) {
        IOException ex = nextException;
        nextException = null;
        throw ex;
      }
      HttpResponse<Void> response = nextResponse;
      nextResponse = null;
      return response;
    }

    void respondWith(int status) {
      this.nextStatus = status;
      this.nextException = null;
      this.nextResponse = new StubResponse(status);
    }

    void throwOnNext(IOException ex) {
      this.nextException = ex;
      this.nextResponse = null;
    }

    URI lastUri() {
      return lastUri;
    }

    String lastBody() {
      return new String(lastBody, StandardCharsets.UTF_8);
    }

    int callCount() {
      return calls;
    }

    private static byte[] drain(HttpRequest.BodyPublisher publisher) {
      if (publisher == null) {
        return new byte[0];
      }
      ByteArrayOutputStream sink = new ByteArrayOutputStream();
      CompletableFuture<Void> done = new CompletableFuture<>();
      publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
          subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
          byte[] chunk = new byte[item.remaining()];
          item.get(chunk);
          sink.writeBytes(chunk);
        }

        @Override
        public void onError(Throwable throwable) {
          done.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
          done.complete(null);
        }
      });
      try {
        done.get();
      } catch (Exception ex) {
        throw new RuntimeException("failed to capture request body", ex);
      }
      return sink.toByteArray();
    }
  }

  private static final class StubResponse implements HttpResponse<Void> {

    private final int status;

    StubResponse(int status) {
      this.status = status;
    }

    @Override
    public int statusCode() {
      return status;
    }

    @Override
    public HttpRequest request() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Void body() {
      return null;
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (k, v) -> true);
    }

    @Override
    public URI uri() {
      throw new UnsupportedOperationException();
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public Optional<HttpResponse<Void>> previousResponse() {
      return Optional.empty();
    }
  }
}
