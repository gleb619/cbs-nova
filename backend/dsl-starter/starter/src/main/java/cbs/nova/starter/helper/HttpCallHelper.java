package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties.Level;
import cbs.nova.starter.config.properties.HttpCallProperties;
import cbs.nova.starter.helper.model.HttpCallContext;
import cbs.nova.starter.helper.model.HttpCallIn;
import cbs.nova.starter.helper.model.HttpCallIn.RedirectPolicy;
import cbs.nova.starter.helper.model.HttpCallOut;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.security.OutboundUrlValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class HttpCallHelper implements Executable<HttpCallIn, HttpCallOut> {

  private static final List<Integer> REDIRECT_STATUSES = List.of(301, 302, 303, 307, 308);

  private final HttpClient client;
  private final CbsNovaLoggingProperties loggingProperties;
  private final HttpCallProperties httpCallProperties;

  /**
   * Convenience constructor with the legacy permissive guard config (no private-address blocking).
   * The Spring-managed bean uses the three-argument Lombok-generated constructor with the
   * {@code cbs.dsl.helper.http-call} bound properties instead.
   */
  @Deprecated(forRemoval = true)
  public HttpCallHelper(HttpClient client, CbsNovaLoggingProperties loggingProperties) {
    this(client, loggingProperties, HttpCallProperties.permissive());
  }

  @Override
  public @NonNull Result<HttpCallOut> execute(@NonNull Context<HttpCallIn> ctx) {
    HttpCallContext call = HttpCallContext.from(ctx.body());

    // Fail closed: redirects are followed (and validated per hop) by this helper. An injected
    // client
    // that follows redirects itself would send requests to unvalidated Location targets.
    if (client.followRedirects() != HttpClient.Redirect.NEVER) {
      return Result.failure(new IllegalStateException(
              "httpCall requires an HttpClient built with Redirect.NEVER (found "
                      + client.followRedirects()
                      + "); the helper validates each redirect hop itself"));
    }

    if (call.url() == null || call.url().isBlank()) {
      return Result.failure(new IllegalArgumentException("httpCall.url is required"));
    }

    HttpRequest request;
    try {
      request = buildRequest(call);
    } catch (IllegalArgumentException e) {
      return Result.failure(e);
    }

    int maxHops = httpCallProperties.maxRedirectHops();
    logRequest(request);
    // Single attempt when maxAttempts resolves to 1 — preserves the pre-T628 byte-for-byte
    // behaviour. The loop body runs exactly once, producing the same Result shape as today.
    int maxAttempts = call.maxAttempts();
    long backoffMillis = call.retryBackoffMillis();
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      if (attempt > 1 && backoffMillis > 0) {
        try {
          Thread.sleep(backoffMillis);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return Result.failure(new HttpCallTransportException(
                  "httpCall interrupted: " + e.getMessage(), e));
        }
      }
      long startedAt = System.nanoTime();
      HttpRequest currentRequest = request;
      HttpResponse<String> response;
      int hops = 0;
      try {
        while (true) {
          response = client.send(currentRequest, BodyHandlers.ofString());
          int status = response.statusCode();
          if (call.redirectPolicy() == RedirectPolicy.NEVER
                  || !REDIRECT_STATUSES.contains(status)) {
            break;
          }
          if (hops >= maxHops) {
            return redirectRejected(request, call,
                    "exceeded maximum redirect hops (%d)".formatted(maxHops));
          }
          String location = response.headers().firstValue("Location").orElse(null);
          if (location == null || location.isBlank()) {
            return redirectRejected(request, call,
                    "redirect response missing Location header");
          }
          URI resolved = currentRequest.uri().resolve(location);
          try {
            OutboundUrlValidator.validate(resolved.toString(), httpCallProperties);
          } catch (IllegalArgumentException e) {
            return Result.failure(new IllegalArgumentException(
                    "httpCall %s %s followed a redirect to a rejected target: %s"
                            .formatted(call.method(), OutboundUrlValidator.sanitize(request.uri()),
                                    e.getMessage()),
                    e));
          }
          if (call.redirectPolicy() == RedirectPolicy.NORMAL
                  && "https".equalsIgnoreCase(currentRequest.uri().getScheme())
                  && "http".equalsIgnoreCase(resolved.getScheme())) {
            return redirectRejected(request, call,
                    "https to http redirect downgrade is not allowed with NORMAL policy");
          }
          boolean sameOrigin = isSameOrigin(currentRequest.uri(), resolved);

          Builder nextBuilder = HttpRequest.newBuilder(resolved)
                  .timeout(Duration.ofMillis(call.timeoutMillis()));
          String nextMethod;
          String nextBody;
          if (status == 307 || status == 308) {
            nextMethod = currentRequest.method();
            nextBody = call.body();
          } else {
            nextMethod = "HEAD".equalsIgnoreCase(currentRequest.method()) ? "HEAD" : "GET";
            nextBody = null;
          }

          for (var entry : currentRequest.headers().map().entrySet()) {
            String name = entry.getKey();
            if (!sameOrigin && isSensitiveHeader(name)) {
              continue;
            }
            if (nextBody == null && isBodyHeader(name)) {
              continue;
            }
            copyHeader(nextBuilder, name, entry.getValue());
          }

          HttpRequest.BodyPublisher publisher = nextBody == null
                  ? BodyPublishers.noBody()
                  : BodyPublishers.ofString(nextBody);
          nextBuilder.method(nextMethod, publisher);
          currentRequest = nextBuilder.build();
          hops++;
        }

        int status = response.statusCode();
        Map<String, String> headers = collectHeaders(response);
        String body = response.body();
        long durationMs = durationMillis(startedAt);
        logResponse(currentRequest, status, durationMs);

        if (call.isValidStatus(status)) {
          return Result.success(new HttpCallOut(status, headers, body, true, null));
        }
        // Non-2xx: retry on 5xx + 429 only; everything else (4xx) fails fast.
        if (attempt < maxAttempts && call.isRetryableStatus(status)) {
          logRetry(request, status, attempt, maxAttempts, durationMs);
          continue;
        }
        return Result.failure(new HttpCallFailure(status, body, headers,
                "httpCall %s %s returned non-2xx status %d".formatted(
                        call.method(), call.url(), status)));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logFailure(currentRequest, e, startedAt);
        return Result.failure(new HttpCallTransportException(
                "httpCall interrupted: " + e.getMessage(), e));
      } catch (IOException e) {
        long durationMs = durationMillis(startedAt);
        logFailure(currentRequest, e, startedAt);
        if (attempt < maxAttempts) {
          logRetry(request, -1, attempt, maxAttempts, durationMs);
          continue;
        }
        return Result.failure(new HttpCallTransportException(
                "httpCall %s %s failed: %s".formatted(
                        call.method(), call.url(), describeCause(e)),
                e));
      } catch (Exception e) {
        logFailure(currentRequest, e, startedAt);
        return Result.failure(new HttpCallTransportException(
                "httpCall %s %s failed: %s".formatted(
                        call.method(), call.url(), describeCause(e)),
                e));
      }
    }
    // Unreachable: the loop returns on every iteration when maxAttempts >= 1, but keep the
    // compiler happy.
    throw new IllegalStateException("httpCall retry loop exited without returning");
  }

  private void logRetry(HttpRequest request, int lastStatusOrMinus, int attempt,
          int maxAttempts, long durationMs) {
    if (!isHttpLevelEnabled(Level.WARN)) {
      return;
    }
    if (lastStatusOrMinus >= 0) {
      log.warn("httpCall retry {}/{} after status {} for {} {} ({}ms); backing off",
              attempt, maxAttempts, lastStatusOrMinus, request.method(), request.uri(), durationMs);
    } else {
      log.warn("httpCall retry {}/{} after transport failure for {} {} ({}ms); backing off",
              attempt, maxAttempts, request.method(), request.uri(), durationMs);
    }
  }

  private @NonNull HttpRequest buildRequest(@NonNull HttpCallContext call) {
    // SSRF guard: scheme allowlist + private-address block + optional host allowlist.
    // Throws IllegalArgumentException, mapped to Result.failure by execute().
    OutboundUrlValidator.validate(call.url(), httpCallProperties);
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
      if (StarterConstants.REQUEST_ID_HEADER.equalsIgnoreCase(entry.getKey())) {
        requestIdSet = true;
      }
    }

    String mdcRequestId = MDC.get(StarterConstants.REQUEST_ID_MDC_KEY);
    if (!requestIdSet && mdcRequestId != null && !mdcRequestId.isBlank()) {
      builder.header(StarterConstants.REQUEST_ID_HEADER, mdcRequestId);
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

  private Result<HttpCallOut> redirectRejected(HttpRequest originalRequest,
          HttpCallContext call, String reason) {
    return Result.failure(new IllegalArgumentException(
            "httpCall %s %s followed a redirect to a rejected target: %s"
                    .formatted(call.method(), OutboundUrlValidator.sanitize(originalRequest.uri()),
                            reason)));
  }

  private static boolean isSameOrigin(URI a, URI b) {
    return Objects.equals(a.getScheme(), b.getScheme())
            && Objects.equals(a.getHost(), b.getHost())
            && a.getPort() == b.getPort();
  }

  private static boolean isSensitiveHeader(String name) {
    return "Authorization".equalsIgnoreCase(name)
            || "Cookie".equalsIgnoreCase(name)
            || "Proxy-Authorization".equalsIgnoreCase(name);
  }

  private static boolean isBodyHeader(String name) {
    return "Content-Length".equalsIgnoreCase(name)
            || "Content-Type".equalsIgnoreCase(name)
            || "Transfer-Encoding".equalsIgnoreCase(name);
  }

  private static void copyHeader(Builder builder, String name, List<String> values) {
    if (values == null || values.isEmpty()) {
      return;
    }
    if (values.size() == 1) {
      builder.header(name, values.get(0));
      return;
    }
    String[] pairs = new String[values.size() * 2];
    int i = 0;
    for (String value : values) {
      pairs[i++] = name;
      pairs[i++] = value;
    }
    builder.headers(pairs);
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
