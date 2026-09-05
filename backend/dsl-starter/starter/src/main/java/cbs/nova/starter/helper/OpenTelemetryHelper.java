package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.starter.annotation.SpringHelper;
import cbs.nova.starter.helper.model.OtelIn;
import cbs.nova.starter.helper.model.OtelOut;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NonNull;

/**
 * Surfaces OpenTelemetry tracing to DSL authors via seven operations selected by {@code mode}:
 *
 * <ul>
 * <li>{@code "span"} — starts a new span and returns its W3C traceparent.</li>
 * <li>{@code "endSpan"} — finalizes a previously started span.</li>
 * <li>{@code "addEvent"} — attaches a named event to an open span.</li>
 * <li>{@code "setBaggage"} / {@code "getBaggage"} — local JVM-scoped key/value store.</li>
 * <li>{@code "injectContext"} — adds W3C trace headers to a carrier map.</li>
 * <li>{@code "extractContext"} — pulls the span-id out of inbound W3C trace headers.</li>
 * </ul>
 *
 * <p>
 * Scope decisions worth knowing:
 *
 * <ul>
 * <li><b>Baggage is local.</b> {@code setBaggage}/{@code getBaggage} are stored in a
 * {@link ConcurrentHashMap} on this singleton-scoped helper for simple business-key correlation
 * inside a single DSL run. They do NOT participate in W3C OTel baggage propagation across services.
 * For cross-service baggage propagation, use {@code injectContext}/{@code extractContext} which
 * thread full W3C traceparent + tracestate via the OpenTelemetry SDK's configured propagators.</li>
 *
 * <li><b>No ambient-context chaining across helper calls.</b> Each {@code span()} invocation starts
 * an independent top-level span. The helper does not use {@code try-with-resources} /
 * {@link Span#makeCurrent()} to thread the new span as the ambient OTel context across subsequent
 * helper invocations — DSL helper calls are stateless per invocation and there is no guaranteed
 * thread/context continuity between them. Parent-child linking between two helper-initiated spans
 * is therefore a known limitation in this helper model, not a bug. Callers that need proper nesting
 * should wrap their own OTel-aware code around the helper calls.</li>
 *
 * <li><b>Lifecycle is fail-fast.</b> {@code endSpan} rejects (does not silently ignore) a second
 * invocation on the same spanId, and rejects any spanId that was never started by this helper
 * instance. This surfaces lifecycle bugs early instead of swallowing them.
 * </ul>
 *
 * <p>
 * The {@link OpenTelemetry} bean is always present (when tracing is disabled it is
 * {@link OpenTelemetry#noop()}, which is a valid tracer that produces no-op spans), so this helper
 * requires no null-check or no-op branch — disabled tracing simply produces no exported spans,
 * which is the desired behavior.
 */
@SpringHelper(name = "otel")
public class OpenTelemetryHelper implements Executable<OtelIn, OtelOut> {

  private static final String INSTRUMENTATION_NAME = "cbs-nova-dsl";
  private static final String TRACEPARENT_VERSION = "00";
  private static final String TRACEPARENT_FLAGS_SAMPLED = "01";
  private static final Set<String> STATUS_CODES = Set.of("OK", "ERROR", "UNSET");
  private static final Set<String> MODES = Set.of(
          "span", "endspan", "addevent", "setbaggage", "getbaggage",
          "injectcontext", "extractcontext");

  private final Tracer tracer;
  private final TextMapPropagator propagator;
  private final TextMapSetter<Map<String, String>> setter = Map::put;
  private final TextMapGetter<Map<String, String>> getter = new TextMapGetter<>() {
    @Override
    public Iterable<String> keys(Map<String, String> carrier) {
      return carrier.keySet();
    }

    @Override
    public String get(Map<String, String> carrier, String key) {
      return carrier == null ? null : carrier.get(key);
    }
  };
  private final Map<String, Span> openSpans = new ConcurrentHashMap<>();
  private final Map<String, String> baggageStore = new ConcurrentHashMap<>();

  public OpenTelemetryHelper(OpenTelemetry openTelemetry) {
    this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
    this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
  }

  @Override
  public @NonNull Result<OtelOut> execute(@NonNull Context<OtelIn> ctx) {
    try {
      OtelIn input = ctx.body();
      if (input == null || input.mode() == null) {
        throw new IllegalArgumentException(
                "otel.mode must be one of " + humanModes()
                        + ", was: " + (input == null ? null : input.mode()));
      }
      String mode = input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "span" -> Result.success(OtelOut.ofString(startSpan(input)));
        case "endspan" -> Result.success(endSpan(input));
        case "addevent" -> Result.success(addEvent(input));
        case "setbaggage" -> Result.success(setBaggage(input));
        case "getbaggage" -> Result.success(getBaggage(input));
        case "injectcontext" -> Result.success(injectContext(input));
        case "extractcontext" -> Result.success(OtelOut.ofString(extractContext(input)));
        default -> throw new IllegalArgumentException(
                "otel.mode must be one of " + humanModes() + ", was: " + input.mode());
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static @NonNull String humanModes() {
    return "span, endSpan, addEvent, setBaggage, getBaggage, injectContext, extractContext";
  }

  private @NonNull String startSpan(@NonNull OtelIn input) {
    String name = input.name();
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("otel.span: name is required");
    }
    Map<String, String> attributes = input.attributes() == null ? Map.of() : input.attributes();
    Span span = tracer.spanBuilder(name).startSpan();
    attributes.forEach(span::setAttribute);
    String spanId = toTraceparent(span.getSpanContext());
    openSpans.put(spanId, span);
    return spanId;
  }

  private @NonNull OtelOut endSpan(@NonNull OtelIn input) {
    String spanId = requireSpanId(input, "endSpan");
    Span span = openSpans.remove(spanId);
    if (span == null) {
      throw new IllegalArgumentException(
              "otel.endSpan: no open span for spanId '" + spanId
                      + "' (already ended or never started)");
    }
    String statusCodeRaw = input.statusCode() == null ? "OK" : input.statusCode();
    String statusUpper = statusCodeRaw.toUpperCase(Locale.ROOT);
    if (!STATUS_CODES.contains(statusUpper)) {
      throw new IllegalArgumentException(
              "otel.endSpan: statusCode must be one of OK, ERROR, UNSET, was: " + statusCodeRaw);
    }
    StatusCode status = StatusCode.valueOf(statusUpper);
    String errorMessage = input.errorMessage();
    span.setStatus(status, errorMessage == null ? "" : errorMessage);
    span.end();
    return OtelOut.ok();
  }

  private @NonNull OtelOut addEvent(@NonNull OtelIn input) {
    String spanId = requireSpanId(input, "addEvent");
    String eventName = input.eventName();
    if (eventName == null || eventName.isBlank()) {
      throw new IllegalArgumentException("otel.addEvent: eventName is required");
    }
    Span span = openSpans.get(spanId);
    if (span == null) {
      throw new IllegalArgumentException(
              "otel.addEvent: no open span for spanId '" + spanId
                      + "' (already ended or never started)");
    }
    Map<String, String> attributes = input.attributes() == null ? Map.of() : input.attributes();
    span.addEvent(eventName, toAttributes(attributes));
    return OtelOut.ok();
  }

  private @NonNull OtelOut setBaggage(@NonNull OtelIn input) {
    String key = input.baggageKey();
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("otel.setBaggage: baggageKey is required");
    }
    if (input.baggageValue() == null) {
      throw new IllegalArgumentException("otel.setBaggage: baggageValue is required");
    }
    baggageStore.put(key, input.baggageValue());
    return OtelOut.ok();
  }

  private @NonNull OtelOut getBaggage(@NonNull OtelIn input) {
    String key = input.baggageKey();
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("otel.getBaggage: baggageKey is required");
    }
    String value = baggageStore.get(key);
    if (value == null) {
      throw new IllegalArgumentException(
              "otel.getBaggage: no value stored for baggageKey '" + key + "'");
    }
    return OtelOut.ofString(value);
  }

  private @NonNull OtelOut injectContext(@NonNull OtelIn input) {
    Map<String, String> carrier = input.headers() == null
            ? new HashMap<>()
            : new HashMap<>(input.headers());
    // Context.current() is typically the root/empty context inside a DSL helper call because we
    // do not thread ambient OTel context across helper invocations (see class Javadoc). When
    // injectContext is called from within an OTel-aware enclosing scope (e.g. a Temporal
    // activity where OpenTelemetryContextPropagator has previously installed a current
    // context via setCurrentContext) the configured W3C propagator will emit a `traceparent`
    // header into the carrier. Otherwise the carrier is returned as-is.
    propagator.inject(io.opentelemetry.context.Context.current(), carrier, setter);
    return OtelOut.ofMap(carrier);
  }

  private @NonNull String extractContext(@NonNull OtelIn input) {
    Map<String, String> headers = input.headers();
    if (headers == null || headers.isEmpty()) {
      return "";
    }
    io.opentelemetry.context.Context extracted = propagator.extract(
            io.opentelemetry.context.Context.root(), headers, getter);
    SpanContext spanContext = Span.fromContext(extracted).getSpanContext();
    if (!spanContext.isValid()) {
      return "";
    }
    return spanContext.getSpanId();
  }

  private static @NonNull String requireSpanId(@NonNull OtelIn input, @NonNull String mode) {
    String spanId = input.spanId();
    if (spanId == null || spanId.isBlank()) {
      throw new IllegalArgumentException("otel." + mode + ": spanId is required");
    }
    return spanId;
  }

  private static @NonNull String toTraceparent(@NonNull SpanContext spanContext) {
    return TRACEPARENT_VERSION + "-" + spanContext.getTraceId() + "-"
            + spanContext.getSpanId() + "-" + TRACEPARENT_FLAGS_SAMPLED;
  }

  private static @NonNull Attributes toAttributes(@NonNull Map<String, String> map) {
    if (map.isEmpty()) {
      return Attributes.empty();
    }
    AttributesBuilder builder = Attributes.builder();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue() == null ? "" : entry.getValue();
      builder.put(AttributeKey.stringKey(key), value);
    }
    return builder.build();
  }
}
