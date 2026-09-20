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

  public static HttpCallIn get(String url) {
    return new HttpCallIn(url, "GET", null, null, null, null, null);
  }

  public static HttpCallIn postJson(String url, String json) {
    return new HttpCallIn(url, "POST",
            Map.of("Content-Type", "application/json"),
            json, null, null, null);
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

  public enum RedirectPolicy {
    NEVER, NORMAL, ALWAYS
  }
}
