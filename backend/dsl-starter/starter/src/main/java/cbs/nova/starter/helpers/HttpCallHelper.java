package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.starter.annotation.SpringHelper;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties.Level;
import cbs.nova.starter.helpers.model.HttpCallContext;
import cbs.nova.starter.helpers.model.HttpCallIn;
import cbs.nova.starter.helpers.model.HttpCallIn.RedirectPolicy;
import cbs.nova.starter.helpers.model.HttpCallOut;
import cbs.nova.starter.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

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

@Slf4j
@RequiredArgsConstructor
@SpringHelper(name = "httpCall")
public class HttpCallHelper implements Executable<HttpCallIn, HttpCallOut> {

  private final HttpClient client;
  private final CbsNovaLoggingProperties loggingProperties;

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

    long startedAt = System.nanoTime();
    logRequest(request);
    try {
      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      int status = response.statusCode();
      Map<String, String> headers = collectHeaders(response);
      String body = response.body();
      long durationMs = durationMillis(startedAt);
      logResponse(request, status, durationMs);

      if (call.isValidStatus(status)) {
        return Result.success(new HttpCallOut(status, headers, body, true, null));
      }
      return Result.failure(new HttpCallFailure(status, body, headers,
              "httpCall %s %s returned non-2xx status %d".formatted(
                      call.method(), call.url(), status)));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logFailure(request, e, startedAt);
      return Result.failure(new HttpCallTransportException(
              "httpCall interrupted: " + e.getMessage(), e));
    } catch (Exception e) {
      logFailure(request, e, startedAt);
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

    boolean requestIdSet = false;
    for (var entry : call.headers().entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      builder.header(entry.getKey(), entry.getValue());
      if (RequestIdFilter.REQUEST_ID_HEADER.equalsIgnoreCase(entry.getKey())) {
        requestIdSet = true;
      }
    }

    String mdcRequestId = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
    if (!requestIdSet && mdcRequestId != null && !mdcRequestId.isBlank()) {
      builder.header(RequestIdFilter.REQUEST_ID_HEADER, mdcRequestId);
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

  private void logRequest(HttpRequest request) {
    if (!isHttpLevelEnabled(Level.DEBUG)) {
      return;
    }
    log.debug("httpCall request {} {}", request.method(), request.uri());
  }

  private void logResponse(HttpRequest request, int status, long durationMs) {
    Level responseLevel = status >= 400 ? Level.WARN : Level.INFO;
    if (!isHttpLevelEnabled(responseLevel)) {
      return;
    }
    String message = "httpCall response {} {} status={} durationMs={}";
    logAt(responseLevel, message, request.method(), request.uri(), status, durationMs);
  }

  private void logFailure(HttpRequest request, Throwable cause, long startedAt) {
    if (!isHttpLevelEnabled(Level.ERROR)) {
      return;
    }
    log.error("httpCall failed {} {} after {}ms: {}", request.method(), request.uri(),
            durationMillis(startedAt), describeCause(cause), cause);
  }

  private boolean isHttpLevelEnabled(Level level) {
    return level.ordinal() >= loggingProperties.http().ordinal();
  }

  private void logAt(Level level, String message, Object... args) {
    switch (level) {
      case DEBUG -> log.debug(message, args);
      case WARN -> log.warn(message, args);
      case ERROR -> log.error(message, args);
      default -> log.info(message, args);
    }
  }

  private static long durationMillis(long startedAtNanos) {
    return (System.nanoTime() - startedAtNanos) / 1_000_000;
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

  public static HttpClient.Redirect toJdkRedirects(RedirectPolicy policy) {
    return switch (policy) {
      case NEVER -> HttpClient.Redirect.NEVER;
      case NORMAL -> HttpClient.Redirect.NORMAL;
      case ALWAYS -> HttpClient.Redirect.ALWAYS;
    };
  }

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

  public static final class HttpCallTransportException extends RuntimeException {
    public HttpCallTransportException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
