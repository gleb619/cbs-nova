package cbs.nova.starter.helper.model;

import cbs.nova.starter.core.StarterConstants;

import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Input for the built-in {@code httpCall} helper.
 *
 * <p>
 * Drives a single JDK {@link java.net.http.HttpClient} invocation. The transport URL is supplied
 * per call so the helper can be pointed at any reachable HTTP endpoint (WireMock, in-process
 * server, public API, etc.).
 *
 * <p>
 * All fields except {@code url} and {@code method} are optional:
 * <ul>
 * <li>{@code headers} — request headers (added as-is, may be empty or null)</li>
 * <li>{@code body} — request body (null for GET/DELETE-style calls)</li>
 * <li>{@code timeoutMillis} — request timeout, defaults to 30_000 when null/&lt;=0</li>
 * <li>{@code followRedirects} — JDK redirect policy hint, defaults to NEVER</li>
 * <li>{@code validStatuses} — optional list of HTTP statuses that should be treated as success,
 * overriding the default 2xx-only behavior</li>
 * <li>{@code maxAttempts} — optional opt-in retry budget. {@code null} or any value {@code <= 1}
 * preserves the legacy single-attempt behaviour. {@code 2} or more enables retries on transport
 * failure (IOException / timeouts) and on retryable HTTP statuses (5xx and 429); non-retryable 4xx
 * statuses fail fast and are not retried.</li>
 * <li>{@code retryBackoffMillis} — optional fixed (non-exponential) delay inserted between retry
 * attempts. {@code null} disables the delay (no sleep), any value is clamped to the inclusive range
 * {@code [0, 30000]} milliseconds — values outside that range are pinned to the nearest endpoint
 * rather than rejected. {@code null} {@code maxAttempts} ({@code <= 1}) makes this field a
 * no-op.</li>
 * </ul>
 */
@AllArgsConstructor
public final class HttpCallIn {

  private final String url;
  private final String method;
  private final @Nullable Map<String, String> headers;
  private final @Nullable String body;
  private final @Nullable Long timeoutMillis;
  private final @Nullable RedirectPolicy followRedirects;
  private final @Nullable List<Integer> validStatuses;
  private final @Nullable Integer maxAttempts;
  private final @Nullable Long retryBackoffMillis;

  /**
   * Legacy constructor — delegates to the full constructor with {@code maxAttempts} and
   * {@code retryBackoffMillis} both {@code null}, preserving the pre-T628 single-attempt behaviour.
   */
  public HttpCallIn(String url, String method, @Nullable Map<String, String> headers,
          @Nullable String body, @Nullable Long timeoutMillis,
          @Nullable RedirectPolicy followRedirects, @Nullable List<Integer> validStatuses) {
    this(url, method, headers, body, timeoutMillis, followRedirects, validStatuses, null, null);
  }

  public String url() {
    return url;
  }

  public String method() {
    return method;
  }

  public @Nullable Map<String, String> headers() {
    return headers;
  }

  public @Nullable String body() {
    return body;
  }

  public @Nullable Long timeoutMillis() {
    return timeoutMillis;
  }

  public @Nullable RedirectPolicy followRedirects() {
    return followRedirects;
  }

  public @Nullable List<Integer> validStatuses() {
    return validStatuses;
  }

  public @Nullable Integer maxAttempts() {
    return maxAttempts;
  }

  public @Nullable Long retryBackoffMillis() {
    return retryBackoffMillis;
  }

  public static HttpCallIn get(String url) {
    return new HttpCallIn(url, "GET", null, null, null, null, null, null, null);
  }

  public static HttpCallIn postJson(String url, String json) {
    return new HttpCallIn(url, "POST",
            Map.of("Content-Type", "application/json"),
            json, null, null, null, null, null);
  }

  public long effectiveTimeoutMillis() {
    if (timeoutMillis == null || timeoutMillis <= 0) {
      return StarterConstants.DEFAULT_TIMEOUT_MILLIS;
    }
    return timeoutMillis;
  }

  public String effectiveMethod() {
    if (method == null || method.isBlank()) {
      return "GET";
    }
    return method.toUpperCase();
  }

  public Map<String, String> effectiveHeaders() {
    return headers == null ? Map.of() : headers;
  }

  public RedirectPolicy effectiveRedirects() {
    return followRedirects == null ? RedirectPolicy.NEVER : followRedirects;
  }

  public List<Integer> effectiveValidStatuses() {
    return validStatuses == null ? List.of() : List.copyOf(validStatuses);
  }

  /**
   * Resolves the effective number of attempts. Treats {@code null} or any value {@code <= 1} as
   * "single attempt" — the legacy behaviour — by pinning the result to {@code 1}. Values
   * {@code >= 2} are returned as-is (no upper cap; the underlying HttpClient already enforces
   * bounds via the per-request timeout).
   */
  public int effectiveMaxAttempts() {
    if (maxAttempts == null || maxAttempts < 2) {
      return 1;
    }
    return maxAttempts;
  }

  /**
   * Resolves the effective retry backoff, clamped to {@code [0, 30000]} milliseconds. {@code null}
   * collapses to {@code 0} (no sleep) so a request that disables retries explicitly isn't slowed
   * down; otherwise the raw value is clamped at both ends rather than rejected — any value above 30
   * seconds is treated as 30 seconds, any negative value as zero.
   */
  public long effectiveRetryBackoffMillis() {
    if (retryBackoffMillis == null) {
      return 0L;
    }
    if (retryBackoffMillis < 0L) {
      return 0L;
    }
    if (retryBackoffMillis > 30_000L) {
      return 30_000L;
    }
    return retryBackoffMillis;
  }

  public enum RedirectPolicy {
    NEVER, NORMAL, ALWAYS
  }
}
