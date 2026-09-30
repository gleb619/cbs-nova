package cbs.nova.starter.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import feign.Request;
import feign.RequestTemplate;
import feign.Target;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Pure-unit tests for {@link ExternalCallFeignInterceptor}. Verifies the request-side capture
 * contract: method, URL, body length, and recorder side-effects; exception path is swallowed
 * because this interceptor must not block a Feign call.
 */
class ExternalCallFeignInterceptorTest {

  private ExternalCallRecorder externalCallRecorder;
  private ExternalCallFeignInterceptor interceptor;

  @BeforeEach
  void setUp() {
    externalCallRecorder = mock(ExternalCallRecorder.class);
    interceptor = new ExternalCallFeignInterceptor(externalCallRecorder);
  }

  private static RequestTemplate template(String method, String baseUrl, String path,
          byte[] body) {
    var tpl = new RequestTemplate().method(Request.HttpMethod.valueOf(method))
            .target(baseUrl)
            .uri(path);
    if (body != null) {
      tpl.body(body, StandardCharsets.UTF_8);
    }
    return tpl;
  }

  private static RequestTemplate templateWithTarget(String method, Target<?> target,
          String path, byte[] body) {
    var tpl = new RequestTemplate().method(Request.HttpMethod.valueOf(method))
            .uri(path);
    tpl.feignTarget(target);
    if (body != null) {
      tpl.body(body, StandardCharsets.UTF_8);
    }
    return tpl;
  }

  @Test
  void applyRecordsGetWithCombinedUrlAndZeroBodyLength() {
    var template = template("GET", "http://example.com", "/ping", null);

    interceptor.apply(template);

    verify(externalCallRecorder).record(
            ExternalCallRecorder.TYPE_HTTP,
            "http://example.com/ping",
            "GET",
            Map.of(
                    StarterConstants.PAYLOAD_METHOD, "GET",
                    StarterConstants.PAYLOAD_URL, "http://example.com/ping",
                    StarterConstants.PAYLOAD_BODY_LENGTH, 0));
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void applyRecordsPostWithCombinedUrlAndBodyLength() {
    var template = template("POST", "http://example.com", "/echo",
            "hello".getBytes(StandardCharsets.UTF_8));

    interceptor.apply(template);

    verify(externalCallRecorder).record(
            ExternalCallRecorder.TYPE_HTTP,
            "http://example.com/echo",
            "POST",
            Map.of(
                    StarterConstants.PAYLOAD_METHOD, "POST",
                    StarterConstants.PAYLOAD_URL, "http://example.com/echo",
                    StarterConstants.PAYLOAD_BODY_LENGTH, 5));
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void applyNormalizesTrailingSlashOnBaseUrl() {
    var template = template("GET", "http://example.com/", "/ping", null);

    interceptor.apply(template);

    verify(externalCallRecorder).record(
            ExternalCallRecorder.TYPE_HTTP,
            "http://example.com/ping",
            "GET",
            Map.of(
                    StarterConstants.PAYLOAD_METHOD, "GET",
                    StarterConstants.PAYLOAD_URL, "http://example.com/ping",
                    StarterConstants.PAYLOAD_BODY_LENGTH, 0));
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void applyUsesAbsolutePathWhenBaseUrlMissing() {
    var template = template("GET", "", "/ping", null);

    interceptor.apply(template);

    verify(externalCallRecorder).record(
            ExternalCallRecorder.TYPE_HTTP,
            "/ping",
            "GET",
            Map.of(
                    StarterConstants.PAYLOAD_METHOD, "GET",
                    StarterConstants.PAYLOAD_URL, "/ping",
                    StarterConstants.PAYLOAD_BODY_LENGTH, 0));
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void applyUsesUpperCasedMethodWhenLowerCaseProvided() {
    var template = new RequestTemplate()
            .method(Request.HttpMethod.GET)
            .target("http://example.com")
            .uri("/ping");

    interceptor.apply(template);

    verify(externalCallRecorder).record(
            ExternalCallRecorder.TYPE_HTTP,
            "http://example.com/ping",
            "GET",
            Map.of(
                    StarterConstants.PAYLOAD_METHOD, "GET",
                    StarterConstants.PAYLOAD_URL, "http://example.com/ping",
                    StarterConstants.PAYLOAD_BODY_LENGTH, 0));
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void applyPassesThroughTemplateMutationsUnchanged() {
    var template = template("GET", "http://example.com", "/ping", null);
    template.header("X-Trace", "abc");

    interceptor.apply(template);

    assertThat(template.headers()).containsKey("X-Trace");
  }

  @Test
  void applySwallowsBodyExceptionAndDoesNotRecord() {
    var template = mock(RequestTemplate.class);
    Mockito.when(template.method()).thenReturn("POST");
    Mockito.when(template.feignTarget()).thenReturn(null);
    Mockito.when(template.url()).thenReturn("/ping");
    Mockito.when(template.body()).thenThrow(new RuntimeException("payload-boom"));

    interceptor.apply(template);

    verifyNoInteractions(externalCallRecorder);
  }

  @Test
  void applyFallsBackToMethodSlashUrlWhenFeignTargetThrows() {
    var template = mock(RequestTemplate.class);
    Mockito.when(template.method()).thenReturn("POST");
    Mockito.when(template.feignTarget()).thenThrow(new RuntimeException("nope"));
    Mockito.when(template.url()).thenReturn("/fallback");
    Mockito.when(template.body()).thenReturn(null);

    interceptor.apply(template);

    verify(externalCallRecorder).record(
            ExternalCallRecorder.TYPE_HTTP,
            "POST /fallback",
            "POST",
            Map.of(
                    StarterConstants.PAYLOAD_METHOD, "POST",
                    StarterConstants.PAYLOAD_URL, "POST /fallback",
                    StarterConstants.PAYLOAD_BODY_LENGTH, 0));
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void applyFallsBackToUnknownWhenBothFeignTargetAndUrlThrow() {
    var template = mock(RequestTemplate.class);
    Mockito.when(template.method()).thenThrow(new RuntimeException("nope-method"));
    Mockito.when(template.feignTarget()).thenThrow(new RuntimeException("nope-target"));
    Mockito.when(template.url()).thenThrow(new RuntimeException("nope-url"));
    Mockito.when(template.body()).thenReturn(null);

    interceptor.apply(template);

    verify(externalCallRecorder).record(
            ExternalCallRecorder.TYPE_HTTP,
            "UNKNOWN",
            "UNKNOWN",
            Map.of(
                    StarterConstants.PAYLOAD_METHOD, "UNKNOWN",
                    StarterConstants.PAYLOAD_URL, "UNKNOWN",
                    StarterConstants.PAYLOAD_BODY_LENGTH, 0));
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void applyFallsBackToUnknownMethodWhenTemplateMethodThrows() {
    var template = mock(RequestTemplate.class);
    Mockito.when(template.method()).thenThrow(new RuntimeException("nope"));
    Mockito.when(template.feignTarget()).thenReturn(null);
    Mockito.when(template.url()).thenReturn("/ping");
    Mockito.when(template.body()).thenReturn(null);

    interceptor.apply(template);

    verify(externalCallRecorder).record(
            ExternalCallRecorder.TYPE_HTTP,
            "/ping",
            "UNKNOWN",
            Map.of(
                    StarterConstants.PAYLOAD_METHOD, "UNKNOWN",
                    StarterConstants.PAYLOAD_URL, "/ping",
                    StarterConstants.PAYLOAD_BODY_LENGTH, 0));
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void applyCombinesBaseUrlAndPathWithoutLeadingSlash() {
    Target<Object> target = new Target<>() {
      @Override
      public Class<Object> type() {
        return Object.class;
      }

      @Override
      public String name() {
        return "stub";
      }

      @Override
      public String url() {
        return "http://example.com";
      }

      @Override
      public feign.Request apply(RequestTemplate t) {
        return t.request();
      }
    };
    var template = templateWithTarget("PUT", target, "ping", null);

    interceptor.apply(template);

    verify(externalCallRecorder).record(
            ExternalCallRecorder.TYPE_HTTP,
            "http://example.com/ping",
            "PUT",
            Map.of(
                    StarterConstants.PAYLOAD_METHOD, "PUT",
                    StarterConstants.PAYLOAD_URL, "http://example.com/ping",
                    StarterConstants.PAYLOAD_BODY_LENGTH, 0));
    verifyNoMoreInteractions(externalCallRecorder);
  }

  @Test
  void applyPayloadMapContainsExpectedKeys() {
    var template = template("DELETE", "http://example.com", "/x", null);

    interceptor.apply(template);

    var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
    verify(externalCallRecorder).record(
            org.mockito.ArgumentMatchers.eq(ExternalCallRecorder.TYPE_HTTP),
            org.mockito.ArgumentMatchers.eq("http://example.com/x"),
            org.mockito.ArgumentMatchers.eq("DELETE"),
            captor.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = captor.getValue();
    assertThat(payload).contains(
            entry(StarterConstants.PAYLOAD_METHOD, "DELETE"),
            entry(StarterConstants.PAYLOAD_URL, "http://example.com/x"),
            entry(StarterConstants.PAYLOAD_BODY_LENGTH, 0));
  }
}
