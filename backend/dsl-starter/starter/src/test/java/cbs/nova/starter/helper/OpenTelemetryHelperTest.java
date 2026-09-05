package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.OtelIn;
import cbs.nova.starter.helper.model.OtelOut;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OpenTelemetryHelperTest {

  private static final Pattern TRACEPARENT = Pattern.compile("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$");
  private static final Pattern SPAN_ID_HEX = Pattern.compile("^[0-9a-f]{16}$");

  @RegisterExtension
  static final OpenTelemetryExtension OTEL = OpenTelemetryExtension.create();

  private final ContextFactory contextFactory = new ContextFactory();
  private OpenTelemetryHelper helper;

  @BeforeEach
  void setUp() {
    helper = new OpenTelemetryHelper(OTEL.getOpenTelemetry());
  }

  @Test
  void spanReturnsWellFormedTraceparent() {
    String traceparent = (String) execute(OtelInSpan("my-span")).value().result();

    assertThat(traceparent).matches(TRACEPARENT);
  }

  @Test
  void spanWithBlankNameFails() {
    Result<OtelOut> result = execute(OtelInSpan(""));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("otel.span: name is required");
  }

  @Test
  void spanWithNullNameFails() {
    Result<OtelOut> result = execute(OtelInSpan(null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("otel.span: name is required");
  }

  @Test
  void endSpanOnValidOpenSpanSucceedsAndExportsSpanWithOkStatus() {
    String traceparent = (String) execute(OtelInSpan("do-work")).value().result();
    Map<String, String> attrs = Map.of("k", "v");
    Result<OtelOut> add = execute(new OtelIn(
            "addEvent", null, attrs, traceparent, null, null, "checkpoint", null, null, null));
    assertThat(add.isSuccess()).isTrue();

    Result<OtelOut> end = execute(new OtelIn(
            "endSpan", null, null, traceparent, "OK", null, null, null, null, null));
    assertThat(end.isSuccess()).isTrue();

    List<SpanData> finished = OTEL.getSpans();
    assertThat(finished).hasSize(1);
    SpanData data = finished.get(0);
    assertThat(data.getName()).isEqualTo("do-work");
    assertThat(data.getStatus()).isEqualTo(StatusData.ok());
    assertThat(data.getEvents())
            .anySatisfy(event -> {
              assertThat(event.getName()).isEqualTo("checkpoint");
              assertThat(event.getAttributes().asMap().toString()).contains("k");
            });
  }

  @Test
  void endSpanSecondCallOnSameIdFails() {
    String traceparent = (String) execute(OtelInSpan("x")).value().result();

    Result<OtelOut> first = execute(new OtelIn(
            "endSpan", null, null, traceparent, null, null, null, null, null, null));
    assertThat(first.isSuccess()).isTrue();

    Result<OtelOut> second = execute(new OtelIn(
            "endSpan", null, null, traceparent, null, null, null, null, null, null));
    assertThat(second.isSuccess()).isFalse();
    assertThat(second.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(second.cause())
            .hasMessageContaining("no open span for spanId '" + traceparent + "'");
  }

  @Test
  void endSpanOnUnknownIdFails() {
    String bogus = "00-00000000000000000000000000000000-0000000000000000-01";
    Result<OtelOut> result = execute(new OtelIn(
            "endSpan", null, null, bogus, null, null, null, null, null, null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("no open span for spanId");
  }

  @Test
  void endSpanWithInvalidStatusCodeFails() {
    String traceparent = (String) execute(OtelInSpan("x")).value().result();

    Result<OtelOut> result = execute(new OtelIn(
            "endSpan", null, null, traceparent, "WAT", null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessageContaining("statusCode must be one of OK, ERROR, UNSET, was: WAT");
  }

  @Test
  void addEventOnUnknownSpanIdFails() {
    String bogus = "00-00000000000000000000000000000000-0000000000000000-01";
    Result<OtelOut> result = execute(new OtelIn(
            "addEvent", null, null, bogus, null, null, "evt", null, null, null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("no open span for spanId");
  }

  @Test
  void addEventWithBlankEventNameFails() {
    String traceparent = (String) execute(OtelInSpan("x")).value().result();

    Result<OtelOut> result = execute(new OtelIn(
            "addEvent", null, null, traceparent, null, null, "", null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("otel.addEvent: eventName is required");
  }

  @Test
  void baggageRoundTripReturnsSameValue() {
    Result<OtelOut> set = execute(new OtelIn(
            "setBaggage", null, null, null, null, null, null, "tenant", "acme", null));
    assertThat(set.isSuccess()).isTrue();

    Result<OtelOut> get = execute(new OtelIn(
            "getBaggage", null, null, null, null, null, null, "tenant", null, null));
    assertThat(get.isSuccess()).isTrue();
    assertThat(get.value().result()).isEqualTo("acme");
  }

  @Test
  void getBaggageOnUnsetKeyFails() {
    Result<OtelOut> result = execute(new OtelIn(
            "getBaggage", null, null, null, null, null, null, "never-set", null, null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("no value stored for baggageKey");
  }

  @Test
  void injectContextWithEmptyHeadersReturnsMapWithoutThrowing() {
    Result<OtelOut> result = execute(new OtelIn(
            "injectContext", null, null, null, null, null, null, null, null, Map.of()));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isInstanceOf(Map.class);
  }

  @Test
  void injectContextWithNullHeadersReturnsMapWithoutThrowing() {
    Result<OtelOut> result = execute(new OtelIn(
            "injectContext", null, null, null, null, null, null, null, null, null));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isInstanceOf(Map.class);
  }

  @Test
  void injectContextEmitsTraceparentWhenAmbientContextIsCurrent() {
    // Drive the helper from inside an OTel ambient context to verify the configured W3C
    // propagator writes a traceparent header. The helper itself does not install ambient
    // contexts (deliberate scope decision), so we exercise the propagator by wrapping the
    // injectContext call inside try-with-resources from the test.
    Span ambient = OTEL.getOpenTelemetry().getTracer("test").spanBuilder("ambient").startSpan();
    try (var scope = ambient.makeCurrent()) {
      Result<OtelOut> result = execute(new OtelIn(
              "injectContext", null, null, null, null, null, null, null, null, new HashMap<>()));
      assertThat(result.isSuccess()).isTrue();
      @SuppressWarnings("unchecked")
      Map<String, String> carrier = (Map<String, String>) result.value().result();
      assertThat(carrier).containsKey("traceparent");
      assertThat(carrier.get("traceparent")).matches(TRACEPARENT);
    } finally {
      ambient.end();
    }
  }

  @Test
  void extractContextWithEmptyHeadersReturnsEmptyString() {
    Result<OtelOut> result = execute(new OtelIn(
            "extractContext", null, null, null, null, null, null, null, null, Map.of()));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("");
  }

  @Test
  void extractContextWithNullHeadersReturnsEmptyString() {
    Result<OtelOut> result = execute(new OtelIn(
            "extractContext", null, null, null, null, null, null, null, null, null));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("");
  }

  @Test
  void extractContextWithValidTraceparentReturnsSpanId() {
    String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
    String spanId = "00f067aa0ba902b7";
    String traceparent = "00-" + traceId + "-" + spanId + "-01";
    Map<String, String> headers = Map.of("traceparent", traceparent);

    Result<OtelOut> result = execute(new OtelIn(
            "extractContext", null, null, null, null, null, null, null, null, headers));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo(spanId);
    assertThat((String) result.value().result()).matches(SPAN_ID_HEX);
  }

  @Test
  void extractContextWithoutTraceparentReturnsEmptyString() {
    Map<String, String> headers = Map.of("x-other", "y");
    Result<OtelOut> result = execute(new OtelIn(
            "extractContext", null, null, null, null, null, null, null, null, headers));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("");
  }

  @Test
  void unknownModeFails() {
    Result<OtelOut> result = execute(new OtelIn(
            "teleport", null, null, null, null, null, null, null, null, null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining(
            "otel.mode must be one of span, endSpan, addEvent, setBaggage, getBaggage,"
                    + " injectContext, extractContext");
  }

  @Test
  void nullModeFails() {
    Result<OtelOut> result = execute(new OtelIn(
            null, null, null, null, null, null, null, null, null, null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("otel.mode must be one of");
  }

  private Result<OtelOut> execute(OtelIn input) {
    return helper.execute(contextFactory.of(input, ExecutionMode.PREVIEW));
  }

  private static OtelIn OtelInSpan(String name) {
    return new OtelIn("span", name, null, null, null, null, null, null, null, null);
  }
}
