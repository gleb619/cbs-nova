package cbs.nova.starter.helper.model;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Output for the built-in {@code httpCall} helper.
 *
 * <p>
 * Mirrors the structured response of a JDK {@link java.net.http.HttpClient} call so the DSL can
 * inspect status, headers, and body without re-parsing.
 *
 * <p>
 * {@code body} is the raw response body as a string (the helper does not attempt to parse JSON /
 * form payloads; that is the role of {@code jsonExtract}). {@code errorMessage} is non-null only
 * when the transport failed (timeout, connection refused, DNS error, etc.) and {@code success} is
 * {@code false} in that case.
 */
public record HttpCallOut(
        int status,
        @Nullable Map<String, String> headers,
        @Nullable String body,
        boolean success,
        @Nullable String errorMessage) {

  /** Successful response with status 2xx. */
  public boolean isSuccess() {
    return success;
  }

  /** Effective response body (never null; empty string if no body). */
  public String bodyOrEmpty() {
    return body == null ? "" : body;
  }

  /** Effective headers map (never null). */
  public Map<String, String> headersOrEmpty() {
    return headers == null ? Map.of() : headers;
  }
}
