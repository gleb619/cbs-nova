package cbs.nova.starter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.entity.NotificationRuleEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import cbs.nova.starter.security.WebhookUrlGuard;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit specs for {@link SlackRuleSink}. The sink is opt-in on
 * {@code cbs.dsl.notifications.slack.webhook-url}: when no URL is configured it records a
 * {@code noop} firing; when configured it POSTs a {@code {"text": ...}} JSON body to the Slack
 * incoming webhook. Non-2xx responses and {@code {"ok":false}} bodies become
 * {@link FiringOutcome#failure} without throwing into the rule engine.
 */
class SlackRuleSinkTest {

  private static final String WEBHOOK_URL = "https://hooks.slack.com/services/T00/B00/secret";

  private final HttpClient httpClient = mock(HttpClient.class);

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void sinkTypeIsSlack() {
    assertThat(newSink(null).sinkType()).isEqualTo("slack");
  }

  @Test
  void deliverRecordsNoopWhenWebhookUrlIsNotConfigured() {
    SlackRuleSink sink = newSink(null);

    FiringOutcome outcome = sink.deliver(rule("loan-failures"),
            new NotificationActionDto("slack", null, null, null), runFailed());

    assertThat(outcome.outcome()).isEqualTo("noop");
    assertThat(outcome.detail()).isEqualTo(SlackRuleSink.NOOP_DETAIL);
    assertThat(outcome.durationMs()).isZero();
  }

  @Test
  void deliverPostsTextJsonBodyAndReturnsSuccessOn2xx() throws Exception {
    SlackRuleSink sink = newSink(WEBHOOK_URL);
    HttpResponse<String> response = response(200, "ok");
    doReturn(response).when(httpClient).send(any(HttpRequest.class),
            any(HttpResponse.BodyHandler.class));

    FiringOutcome outcome = sink.deliver(rule("loan-failures"),
            new NotificationActionDto("slack", null, null, null), runFailed());

    assertThat(outcome.outcome()).isEqualTo("success");
    assertThat(outcome.detail()).isEqualTo("http 200");
    assertThat(outcome.durationMs()).isGreaterThanOrEqualTo(0L);
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
    HttpRequest request = captor.getValue();
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.uri().toString()).isEqualTo(WEBHOOK_URL);
    assertThat(request.headers().firstValue("Content-Type")).hasValue("application/json");
    assertThat(bodyOf(request))
            .isEqualTo("{\"text\":\"Rule 'loan-failures': RunFailed on run/run-1\"}");
  }

  @Test
  void non2xxResponseBecomesFailureWithResponseSnippet() throws Exception {
    SlackRuleSink sink = newSink(WEBHOOK_URL);
    HttpResponse<String> response = response(500, "no_service");
    doReturn(response).when(httpClient).send(any(HttpRequest.class),
            any(HttpResponse.BodyHandler.class));

    FiringOutcome outcome = sink.deliver(rule("r"),
            new NotificationActionDto("slack", null, null, null), runFailed());

    assertThat(outcome.outcome()).isEqualTo("failure");
    assertThat(outcome.detail()).contains("http 500").contains("no_service");
    assertThat(outcome.durationMs()).isGreaterThanOrEqualTo(0L);
  }

  @Test
  void okFalseBodyBecomesFailureWithResponseSnippet() throws Exception {
    SlackRuleSink sink = newSink(WEBHOOK_URL);
    HttpResponse<String> response = response(200,
            "{\"ok\":false,\"error\":\"channel_not_found\"}");
    doReturn(response).when(httpClient).send(any(HttpRequest.class),
            any(HttpResponse.BodyHandler.class));

    FiringOutcome outcome = sink.deliver(rule("r"),
            new NotificationActionDto("slack", null, null, null), runFailed());

    assertThat(outcome.outcome()).isEqualTo("failure");
    assertThat(outcome.detail()).contains("slack ok=false")
            .contains("channel_not_found");
    assertThat(outcome.durationMs()).isGreaterThanOrEqualTo(0L);
  }

  @Test
  void disabledSinkNeverCallsHttpClient() {
    SlackRuleSink sink = newSink(null);

    sink.deliver(rule("r"), new NotificationActionDto("slack", null, null, null), runFailed());

    verifyNoInteractions(httpClient);
  }

  private SlackRuleSink newSink(String webhookUrl) {
    return new SlackRuleSink(httpClient, objectMapper, Duration.ofSeconds(5),
            WebhookUrlGuard.permissiveProperties(), webhookUrl);
  }

  @SuppressWarnings("unchecked")
  private static HttpResponse<String> response(int status, String body) {
    HttpResponse<String> response = mock(HttpResponse.class);
    doReturn(status).when(response).statusCode();
    doReturn(body).when(response).body();
    return response;
  }

  private static String bodyOf(HttpRequest request) {
    HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
    CompletableFuture<byte[]> future = new CompletableFuture<>();
    publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
      private final List<ByteBuffer> buffers = new ArrayList<>();

      @Override
      public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
      }

      @Override
      public void onNext(ByteBuffer item) {
        buffers.add(item.duplicate());
      }

      @Override
      public void onError(Throwable throwable) {
        future.completeExceptionally(throwable);
      }

      @Override
      public void onComplete() {
        int size = buffers.stream().mapToInt(ByteBuffer::remaining).sum();
        byte[] bytes = new byte[size];
        int offset = 0;
        for (ByteBuffer buffer : buffers) {
          int remaining = buffer.remaining();
          buffer.get(bytes, offset, remaining);
          offset += remaining;
        }
        future.complete(bytes);
      }
    });
    try {
      return new String(future.get(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private static NotificationRuleEntity rule(String name) {
    Instant now = Instant.now();
    return new NotificationRuleEntity(1L, name, true, "RunFailed", null, null, null, null, "[]",
            0, "default", now, now);
  }

  private static DomainEvent.RunFailed runFailed() {
    Instant now = Instant.now();
    return new DomainEvent.RunFailed("run-1", "LoanOrigination", DslRunStatus.FAILED, "boom", now,
            now, null, null);
  }
}
