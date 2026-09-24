package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties.Level;
import cbs.nova.starter.config.properties.HttpCallProperties;
import cbs.nova.starter.exception.ApiKeyNotFoundException;
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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class HttpCallHelper implements Executable<HttpCallIn, HttpCallOut> {

  private final HttpClient client;
  private final CbsNovaLoggingProperties loggingProperties;
  private final HttpCallProperties httpCallProperties;
  private Map<RedirectPolicy, HttpClient> clientsByPolicy;

  /**
   * Convenience constructor with the legacy permissive guard config (no private-address blocking).
   * The Spring-managed bean uses the three-argument Lombok-generated constructor with the
   * {@code cbs.dsl.helper.http-call} bound properties instead. The per-policy client snapshot is
   * built eagerly here (post-delegation) so legacy call sites keep their eager-snapshot semantics.
   */
  //TODO: replace ctor with lomboks one
  @Deprecated(forRemoval = true)
  public HttpCallHelper(HttpClient client, CbsNovaLoggingProperties loggingProperties) {
    this(client, loggingProperties, HttpCallProperties.permissive());
    this.clientsByPolicy = buildClientsByPolicy(client);
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

    HttpClient selectedClient = clientsByPolicy().get(call.redirectPolicy());
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
      try {
        HttpResponse<String> response = selectedClient.send(request, BodyHandlers.ofString());
        Result<HttpCallOut> redirectRejection = validateRedirectTarget(request, response, call);
        if (redirectRejection != null) {
          return redirectRejection;
        }
        int status = response.statusCode();
        Map<String, String> headers = collectHeaders(response);
        String body = response.body();
        long durationMs = durationMillis(startedAt);
        logResponse(request, status, durationMs);

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
        logFailure(request, e, startedAt);
        return Result.failure(new HttpCallTransportException(
                "httpCall interrupted: " + e.getMessage(), e));
      } catch (IOException e) {
        long durationMs = durationMillis(startedAt);
        logFailure(request, e, startedAt);
        if (attempt < maxAttempts) {
          logRetry(request, -1, attempt, maxAttempts, durationMs);
          continue;
        }
        return Result.failure(new HttpCallTransportException(
                "httpCall %s %s failed: %s".formatted(
                        call.method(), call.url(), describeCause(e)),
                e));
      } catch (Exception e) {
        logFailure(request, e, startedAt);
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

  /**
   * Re-validates the final URI after a followed redirect.
   *
   * <p>
   * Honest limitation: this is detection, not prevention. The JDK {@link HttpClient} has already
   * followed the redirect (and received the response body) before we see {@code response.uri()}, so
   * a redirect to a blocked address is caught only after the fact — the same TOCTOU gap as the
   * best-effort DNS check. Closing it fully needs a custom redirect-following interceptor inside
   * the client (follow-up). Until then, use {@link RedirectPolicy#NEVER} for untrusted targets and
   * validate the {@code Location} yourself. Returns {@code null} when the call is acceptable.
   */
  private Result<HttpCallOut> validateRedirectTarget(HttpRequest request,
          HttpResponse<String> response, HttpCallContext call) {
    if (call.redirectPolicy() == RedirectPolicy.NEVER
            || response.uri().equals(request.uri())) {
      return null;
    }
    try {
      OutboundUrlValidator.validate(response.uri().toString(), httpCallProperties);
      return null;
    } catch (IllegalArgumentException e) {
      return Result.failure(new IllegalArgumentException(
              "httpCall %s %s followed a redirect to a rejected target: %s"
                      .formatted(call.method(), OutboundUrlValidator.sanitize(request.uri()),
                              e.getMessage()),
              e));
    }
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

  /**
   * JDK HttpClient is configured once at build time and exposes no public config getters
   * (connect timeout, executor, SSL context, etc.), so we cannot derive per-policy variants from
   * the injected client. Instead we build one client per RedirectPolicy up front and cache the
   * immutable snapshot in {@code clientsByPolicy}. The injected {@code client} is reused for
   * {@code NEVER} since the JDK default redirect policy is NEVER — building a second identical
   * client would just waste resources.
   *
   * <p>
   * For the two-argument convenience ctor the snapshot is built eagerly post-delegation; for the
   * Lombok-generated three-argument ctor (the Spring path) it is built lazily on first access via
   * {@link #clientsByPolicy()}. Both paths share the same immutable result, so HTTP client
   * policy behaviour is unchanged.
   */
  private static Map<RedirectPolicy, HttpClient> buildClientsByPolicy(HttpClient client) {
    Map<RedirectPolicy, HttpClient> map = new EnumMap<>(RedirectPolicy.class);
    map.put(RedirectPolicy.NEVER, client);
    map.put(RedirectPolicy.NORMAL,
            HttpClient.newBuilder().followRedirects(toJdkRedirects(RedirectPolicy.NORMAL)).build());
    map.put(RedirectPolicy.ALWAYS,
            HttpClient.newBuilder().followRedirects(toJdkRedirects(RedirectPolicy.ALWAYS)).build());
    return Map.copyOf(map);
  }

  private Map<RedirectPolicy, HttpClient> clientsByPolicy() {
    Map<RedirectPolicy, HttpClient> snapshot = clientsByPolicy;
    if (snapshot == null) {
      snapshot = buildClientsByPolicy(client);
      clientsByPolicy = snapshot;
    }
    return snapshot;
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
