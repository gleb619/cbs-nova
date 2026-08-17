package cbs.nova.starter.helpers.model;

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
 * </ul>
 */
public record HttpCallIn(
        String url,
        String method,
        @Nullable Map<String, String> headers,
        @Nullable String body,
        @Nullable Long timeoutMillis,
        @Nullable RedirectPolicy followRedirects,
        @Nullable List<Integer> validStatuses) {

  /** Convenience constructor for callers that do not need {@code validStatuses}. */
  public HttpCallIn(
          String url,
          String method,
          @Nullable Map<String, String> headers,
          @Nullable String body,
          @Nullable Long timeoutMillis,
          @Nullable RedirectPolicy followRedirects) {
    this(url, method, headers, body, timeoutMillis, followRedirects, null);
  }

  /** Convenience factory for the common GET case. */
  public static HttpCallIn get(String url) {
    return new HttpCallIn(url, "GET", null, null, null, null);
  }

  /** Convenience factory for a JSON POST. */
  public static HttpCallIn postJson(String url, String json) {
    return new HttpCallIn(url, "POST",
            Map.of("Content-Type", "application/json"),
            json, null, null);
  }

  /** Effective request timeout in milliseconds; never zero or negative. */
  public long effectiveTimeoutMillis() {
    if (timeoutMillis == null || timeoutMillis <= 0) {
      return DEFAULT_TIMEOUT_MILLIS;
    }
    return timeoutMillis;
  }

  /** Effective HTTP method (upper-cased), defaulting to GET. */
  public String effectiveMethod() {
    if (method == null || method.isBlank()) {
      return "GET";
    }
    return method.toUpperCase();
  }

  /** Effective headers map (never null). */
  public Map<String, String> effectiveHeaders() {
    return headers == null ? Map.of() : headers;
  }

  /** Effective redirect policy; defaults to NEVER. */
  public RedirectPolicy effectiveRedirects() {
    return followRedirects == null ? RedirectPolicy.NEVER : followRedirects;
  }

  /** Effective list of valid HTTP statuses (never null). */
  public List<Integer> effectiveValidStatuses() {
    return validStatuses == null ? List.of() : List.copyOf(validStatuses);
  }

  public static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

  /** Lightweight redirect policy enum (mapped to JDK constants at execution time). */
  public enum RedirectPolicy {
    NEVER, NORMAL, ALWAYS
  }

  /** Header keys commonly referenced by callers / tests. */
  public static final class HeaderNames {
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String ACCEPT = "Accept";
    public static final String AUTHORIZATION = "Authorization";
    public static final String X_REQUEST_ID = "X-Request-Id";
    public static final List<String> ALL = List.of(CONTENT_TYPE, ACCEPT, AUTHORIZATION,
            X_REQUEST_ID);

    private HeaderNames() {
    }
  }
}
