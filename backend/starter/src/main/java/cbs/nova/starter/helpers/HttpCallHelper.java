package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helpers.model.HttpCallContext;
import cbs.nova.starter.helpers.model.HttpCallIn;
import cbs.nova.starter.helpers.model.HttpCallIn.RedirectPolicy;
import cbs.nova.starter.helpers.model.HttpCallOut;
import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in helper for making a single real-HTTP call from a DSL flow.
 *
 * <p>
 * Mirrors the {@code unreliableApi} helper's structure (record in/out, {@code Executable} contract,
 * {@code @Helper} name) but reaches out to a real network endpoint through the JDK
 * {@link HttpClient} instead of an in-process failure simulator. The integration test
 * {@code HttpResilienceDslIntegrationTest} points the helper at a WireMock instance to exercise
 * Temporal retry / compensation against real 5xx and timeout failures.
 *
 * <p>
 * Contract:
 * <ul>
 * <li>2xx, or a status listed in {@link HttpCallIn#validStatuses()}, → {@link Result#success} with
 * the response in {@link HttpCallOut}. Non-2xx → {@link Result#failure} so callers (transactions,
 * processes) can map it to retry / compensation.</li>
 * <li>I/O failure (timeout, connection refused, DNS) → {@link Result#failure} with the underlying
 * exception wrapped.</li>
 * </ul>
 *
 * <p>
 * Faking: this helper never injects {@code ExternalCallRecorder}. To short-circuit it without a
 * real network call, declare a startup fake in {@code application.yml} with {@code type: helper},
 * {@code code: httpCall}; the {@code HelperInterceptor} returns the configured response before the
 * helper runs.
 */
@Helper(name = "httpCall")
public class HttpCallHelper implements Executable<HttpCallIn, HttpCallOut> {

  private final HttpClient client;

  /** Constructor for injecting a pre-configured {@link HttpClient}. */
  public HttpCallHelper(HttpClient client) {
    this.client = client;
  }

  @Override
  public @NonNull Result<HttpCallOut> execute(@NonNull Context<HttpCallIn> ctx) {
    HttpCallContext call = HttpCallContext.from(ctx.body());

    if (call.url() == null || call.url().isBlank()) {
      return Result.failure(new IllegalArgumentException("httpCall.url is required"));
    }

    HttpRequest request;
    try {
      request = buildRequest(call);
    } catch (IllegalArgumentException e) {
      return Result.failure(e);
    }

    try {
      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      int status = response.statusCode();
      Map<String, String> headers = collectHeaders(response);
      String body = response.body();

      if (call.isValidStatus(status)) {
        return Result.success(new HttpCallOut(status, headers, body, true, null));
      }
      return Result.failure(new HttpCallFailure(status, body, headers,
              "httpCall %s %s returned non-2xx status %d".formatted(
                      call.method(), call.url(), status)));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.failure(new HttpCallTransportException(
              "httpCall interrupted: " + e.getMessage(), e));
    } catch (Exception e) {
      return Result.failure(new HttpCallTransportException(
              "httpCall %s %s failed: %s".formatted(
                      call.method(), call.url(), describeCause(e)),
              e));
    }
  }

  private static @NonNull HttpRequest buildRequest(@NonNull HttpCallContext call) {
    URI uri;
    try {
      uri = URI.create(call.url());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("httpCall.url is not a valid URI: " + call.url(), e);
    }

    Builder builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMillis(call.timeoutMillis()));

    for (var entry : call.headers().entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      builder.header(entry.getKey(), entry.getValue());
    }

    HttpRequest.BodyPublisher publisher = call.body() == null
            ? BodyPublishers.noBody()
            : BodyPublishers.ofString(call.body());
    String method = call.method();
    try {
      builder.method(method, publisher);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
              "httpCall.method is not a valid HTTP method: " + method, e);
    }
    return builder.build();
  }

  private static @NonNull Map<String, String> collectHeaders(
          @NonNull HttpResponse<?> response) {
    var copy = new LinkedHashMap<String, String>();
    response.headers().map().forEach((name, values) -> {
      if (values == null || values.isEmpty()) {
        return;
      }
      copy.put(name, String.join(",", values));
    });
    return copy;
  }

  private static @NonNull String describeCause(@NonNull Throwable t) {
    String message = t.getMessage();
    if (message == null || message.isBlank()) {
      return t.getClass().getSimpleName();
    }
    return t.getClass().getSimpleName() + ": " + message;
  }

  /**
   * Translates the supplied {@link RedirectPolicy} into the JDK {@link HttpClient.Redirect}
   * constant.
   */
  public static HttpClient.Redirect toJdkRedirects(RedirectPolicy policy) {
    return switch (policy) {
      case NEVER -> HttpClient.Redirect.NEVER;
      case NORMAL -> HttpClient.Redirect.NORMAL;
      case ALWAYS -> HttpClient.Redirect.ALWAYS;
    };
  }

  /** Failure marker thrown for non-2xx HTTP responses; carries status + body. */
  public static final class HttpCallFailure extends RuntimeException {
    private final int status;
    private final String body;
    private final Map<String, String> headers;

    public HttpCallFailure(int status, String body, Map<String, String> headers, String message) {
      super(message);
      this.status = status;
      this.body = body;
      this.headers = headers;
    }

    public int status() {
      return status;
    }

    public String body() {
      return body;
    }

    public Map<String, String> headers() {
      return headers;
    }
  }

  /** Failure marker thrown for transport-layer I/O problems. */
  public static final class HttpCallTransportException extends RuntimeException {
    public HttpCallTransportException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
