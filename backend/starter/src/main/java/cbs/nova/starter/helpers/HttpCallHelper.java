package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
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
 * <li>2xx → {@link Result#success} with the response in {@link HttpCallOut}.</li>
 * <li>non-2xx (including 5xx and 4xx) → {@link Result#failure} so callers (transactions, processes)
 * can map it to retry / compensation.</li>
 * <li>I/O failure (timeout, connection refused, DNS) → {@link Result#failure} with the underlying
 * exception wrapped.</li>
 * </ul>
 */
@Helper(name = "httpCall")
public class HttpCallHelper implements Executable<HttpCallIn, HttpCallOut> {

  private final HttpClient client;

  /** Production constructor: builds a fresh {@link HttpClient}. */
  public HttpCallHelper() {
    this(HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build());
  }

  /** Test-friendly constructor for injecting a pre-configured client. */
  public HttpCallHelper(HttpClient client) {
    this.client = client;
  }

  @Override
  public @NonNull Result<HttpCallOut> execute(@NonNull Context<HttpCallIn> ctx) {
    HttpCallIn input = ctx.body();
    if (input == null) {
      return Result.failure(new IllegalArgumentException("httpCall input is required"));
    }
    if (input.url() == null || input.url().isBlank()) {
      return Result.failure(new IllegalArgumentException("httpCall.url is required"));
    }

    HttpRequest request;
    try {
      request = buildRequest(input);
    } catch (IllegalArgumentException e) {
      return Result.failure(e);
    }

    try {
      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      int status = response.statusCode();
      Map<String, String> headers = collectHeaders(response);
      String body = response.body();

      if (status >= 200 && status < 300) {
        return Result.success(new HttpCallOut(status, headers, body, true, null));
      }
      return Result.failure(new HttpCallFailure(status, body, headers,
              "httpCall %s %s returned non-2xx status %d".formatted(
                      input.effectiveMethod(), input.url(), status)));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.failure(new HttpCallTransportException(
              "httpCall interrupted: " + e.getMessage(), e));
    } catch (Exception e) {
      return Result.failure(new HttpCallTransportException(
              "httpCall %s %s failed: %s".formatted(
                      input.effectiveMethod(), input.url(), describeCause(e)),
              e));
    }
  }

  private static @NonNull HttpRequest buildRequest(@NonNull HttpCallIn input) {
    URI uri;
    try {
      uri = URI.create(input.url());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("httpCall.url is not a valid URI: " + input.url(), e);
    }

    Builder builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMillis(input.effectiveTimeoutMillis()));

    for (var entry : input.effectiveHeaders().entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      builder.header(entry.getKey(), entry.getValue());
    }

    HttpRequest.BodyPublisher publisher = input.body() == null
            ? BodyPublishers.noBody()
            : BodyPublishers.ofString(input.body());
    String method = input.effectiveMethod();
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
