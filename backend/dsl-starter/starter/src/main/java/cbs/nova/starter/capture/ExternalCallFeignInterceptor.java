package cbs.nova.starter.capture;

import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Target;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ExternalCallFeignInterceptor implements RequestInterceptor {

  private final @NonNull ExternalCallRecorder externalCallRecorder;

  @Override
  public void apply(@NonNull RequestTemplate template) {
    try {
      var method = safeMethod(template);
      var url = safeUrl(template);
      var payload = buildPayload(method, url, template.body());

      // HTTP response faking happens at the helper boundary; this interceptor only observes.
      externalCallRecorder.record("http", url, method, payload);
    } catch (Exception ex) {
      log.debug("Failed to record Feign HTTP call", ex);
    }
  }

  private @NonNull String safeMethod(@NonNull RequestTemplate template) {
    try {
      var method = template.method();
      return method != null ? method.toUpperCase() : "UNKNOWN";
    } catch (Exception ex) {
      return "UNKNOWN";
    }
  }

  private @NonNull String safeUrl(@NonNull RequestTemplate template) {
    try {
      var target = template.feignTarget();
      var baseUrl = target != null ? target.url() : null;
      var path = template.url();
      return combineUrl(baseUrl, path);
    } catch (Exception ex) {
      try {
        return template.method() + " " + template.url();
      } catch (Exception fallback) {
        return "UNKNOWN";
      }
    }
  }

  private @NonNull String combineUrl(@Nullable String baseUrl, @Nullable String path) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return path != null && !path.isBlank() ? path : "UNKNOWN";
    }
    var normalizedBase = baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
    var normalizedPath = path != null && !path.isBlank() ? path : "";
    if (normalizedPath.startsWith("/")) {
      return normalizedBase + normalizedPath;
    }
    if (normalizedPath.isEmpty()) {
      return normalizedBase;
    }
    return normalizedBase + "/" + normalizedPath;
  }

  private @NonNull Map<String, Object> buildPayload(
          @NonNull String method, @NonNull String url, @Nullable byte[] body) {
    var payload = new HashMap<String, Object>();
    payload.put("method", method);
    payload.put("url", url);
    payload.put("bodyLength", body != null ? body.length : 0);
    return payload;
  }
}
