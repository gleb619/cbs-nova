package cbs.nova.starter.vhs.replay;

import cbs.nova.starter.vhs.TapeEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.core.JacksonException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link VhsCallDriver} that re-invokes the recorded call against a local backend instance over
 * HTTP.
 *
 * <p>
 * Process-run calls ({@code type=process}) are replayed as {@code POST /api/dsl/run/{target}} with
 * the recorded input as the body. Helper calls have no public HTTP execution endpoint yet; they are
 * reported as an explicit, visible failure in the replay report (never silently dropped).
 *
 * <p>
 * Only ever construct this driver for the {@code local} target (dev/staging backend) through
 * {@link VhsCallDrivers}, which enforces the two-key production opt-in for any non-local target.
 */
@Slf4j
public final class LocalBackendCallDriver implements VhsCallDriver {

  private final HttpClient httpClient;
  private final String baseUrl;
  private final Duration timeout;
  private final ObjectMapper objectMapper;

  public LocalBackendCallDriver(
          @NonNull String baseUrl, long requestTimeoutMs, @NonNull ObjectMapper objectMapper) {
    this(baseUrl, requestTimeoutMs, objectMapper, HttpClient.newHttpClient());
  }

  LocalBackendCallDriver(
          @NonNull String baseUrl,
          long requestTimeoutMs,
          @NonNull ObjectMapper objectMapper,
          @NonNull HttpClient httpClient) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.timeout = Duration.ofMillis(Math.max(1, requestTimeoutMs));
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  @Override
  public CallResult execute(TapeEvent callStartEvent) {
    TapeEvent.CallMetadata meta = callStartEvent.callMetadata();
    if (meta == null) {
      return CallResult.failure("call_start event has no call_metadata");
    }
    String path = resolvePath(meta);
    if (path == null) {
      return CallResult.failure(
              "call type '" + meta.type() + "' has no replayable HTTP endpoint yet (call="
                      + meta.callId() + ")");
    }
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("operation", meta.operation());
      body.put("input", callStartEvent.input());
      HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
              .timeout(timeout)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
              .build();
      HttpResponse<byte[]> response = httpClient.send(request,
              HttpResponse.BodyHandlers.ofByteArray());
      int status = response.statusCode();
      if (status >= 200 && status < 300) {
        Object output = parseBody(response.body());
        return CallResult.success(output);
      }
      return CallResult.failure("target returned HTTP " + status + " for " + path);
    } catch (JacksonException ex) {
      return CallResult.failure("failed to serialize replay call body: " + ex.getMessage());
    } catch (IOException ex) {
      return CallResult.failure("I/O error calling " + path + ": " + ex.getMessage());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return CallResult.failure("interrupted calling " + path);
    }
  }

  @Nullable
  private static String resolvePath(TapeEvent.CallMetadata meta) {
    String type = meta.type() == null ? "" : meta.type().toLowerCase();
    String target = meta.target() == null ? "" : meta.target();
    if (target.isBlank()) {
      return null;
    }
    if (type.equals("process") || type.equals("run")) {
      return "/api/dsl/run/" + target;
    }
    return null;
  }

  @Nullable
  private Object parseBody(byte[] body) {
    if (body == null || body.length == 0) {
      return null;
    }
    try {
      return objectMapper.readTree(body);
    } catch (JacksonException ex) {
      log.debug("Target returned non-JSON body ({} bytes); recording as null output", body.length);
      return null;
    }
  }
}
