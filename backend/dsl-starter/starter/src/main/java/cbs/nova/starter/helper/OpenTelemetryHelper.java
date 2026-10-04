package cbs.nova.starter.helper;

import static cbs.nova.starter.core.StarterConstants.OTEL_INSTRUMENTATION_NAME;
import static cbs.nova.starter.core.StarterConstants.TRACEPARENT_FLAGS_SAMPLED;
import static cbs.nova.starter.core.StarterConstants.TRACEPARENT_VERSION;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.starter.annotation.SpringHelper;
import cbs.nova.starter.config.properties.OpenTelemetryHelperProperties;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.NonNull;

/**
 * Surfaces OpenTelemetry tracing to DSL authors via seven operations selected by {@code mode}:
 *
 * <ul>
 * <li>{@code "span"} — starts a new span and returns its W3C traceparent.</li>
 * <li>{@code "endSpan"} — finalizes a previously started span.</li>
 * <li>{@code "addEvent"} — attaches a named event to an open span.</li>
 * <li>{@code "setBaggage"} / {@code "getBaggage"} — JVM-local per-run value store.</li>
 * <li>{@code "injectContext"} — adds W3C trace headers to a carrier map.</li>
 * <li>{@code "extractContext"} — pulls the span-id out of inbound W3C trace headers.</li>
 * </ul>
 *
 * <p>
 * Scope decisions worth knowing:
 *
 * <ul>
 * <li><b>Baggage is local and scoped per run.</b> {@code setBaggage}/{@code getBaggage} are stored
 * in a process-wide map on this singleton-scoped helper, partitioned by the {@link Context#runId()
 * runId} of the DSL run that set the value. A key set by run A is NOT visible to run B. Entries are
 * bounded in count and TTL ({@link OpenTelemetryHelperProperties#baggageMaxRuns} /
 * {@link OpenTelemetryHelperProperties#baggageTtl}). They do NOT participate in W3C OTel baggage
 * propagation across services. For cross-service baggage propagation, use
 * {@code injectContext}/{@code extractContext} which thread full W3C traceparent + tracestate via
 * the OpenTelemetry SDK's configured propagators.</li>
 *
 * <li><b>Open-span registry is bounded.</b> {@code span()} inserts into a bounded,
 * insertion-ordered map. Lazy cleanup runs on every
 * {@code span()}/{@code endSpan()}/{@code addEvent()} call (no scheduler) and either silently reaps
 * entries older than {@link OpenTelemetryHelperProperties#spanTtl} — ending them with
 * {@code ERROR}/"abandoned" so exporters still see them — or, when the map is at
 * {@link OpenTelemetryHelperProperties#spanMaxEntries}, ends the oldest entry with
 * {@code ERROR}/"evicted" before inserting the new one. Each {@code Span#end()} call is guarded to
 * run at most once per span, regardless of which path triggers it.</li>
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
 * invocation on the same spanId against a still-open span, and rejects any spanId that was never
 * started by this helper instance. However, a {@code endSpan} that arrives AFTER the helper has
 * already ended the span itself (TTL-reap or max-entries eviction) is a silent no-op: the entry has
 * already been removed from the registry and {@code Span.end()} already called, so we cannot
 * double-end it.</li>
 * </ul>
 *
 * <p>
 * The {@link OpenTelemetry} bean is always present (when tracing is disabled it is
 * {@link OpenTelemetry#noop()}, which is a valid tracer that produces no-op spans), so this helper
 * requires no null-check or no-op branch — disabled tracing simply produces no exported spans,
 * which is the desired behavior.
 */
@SpringHelper(name = "otel", description = "Exposes OpenTelemetry tracing operations (span, attribute, log) to DSL authors.")
public class OpenTelemetryHelper implements Executable<OtelIn, OtelOut> {

  private static final Set<String> STATUS_CODES = Set.of("OK", "ERROR", "UNSET");

  /**
   * Marker for spans reaped because they sat open past
   * {@link OpenTelemetryHelperProperties#spanTtl}.
   */
  private static final String SPAN_ABANDONED = "abandoned";
  /**
   * Marker for spans reaped because {@link OpenTelemetryHelperProperties#spanMaxEntries} was
   * reached.
   */
  private static final String SPAN_EVICTED = "evicted";

  /**
   * Separator between run-id and bag-key inside the composite flat-map key. {@code U+0000} is
   * illegal in DSL values everywhere we govern, so it can never appear in either component.
   */
  private static final char BAGGAGE_KEY_SEPARATOR = '\0';

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
  private final OpenTelemetryHelperProperties properties;
  private final Clock clock;

  /**
   * Open spans keyed by traceparent. Insertion-ordered so eviction can pick the oldest entry. The
   * map is NOT itself thread-safe, so every read/mutate site must synchronize on its monitor; the
   * cleanup helpers below carry that contract in their Javadoc.
   */
  private final Map<String, SpanEntry> openSpans = new LinkedHashMap<>();
  /**
   * Per-run baggage as a flat map with composite keys ({@code runId + U+0000 + baggageKey} → value
   * + setAt). Bounded in size and TTL.
   */
  private final Map<String, BaggageEntry> baggageStore = new LinkedHashMap<>();
  /**
   * Span-ids the helper has already ended itself (TTL-reap or eviction). A late {@code endSpan} for
   * one of these is a silent no-op rather than an error: the entry is gone from {@link #openSpans}
   * AND {@code Span.end()} already ran, so we cannot re-end it.
   */
  /**
   * Ids of spans this helper ended itself (TTL/eviction), so a late {@code endSpan} for them is a
   * silent no-op. Bounded to {@code spanMaxEntries} (oldest forgotten first) and guarded by the
   * {@link #openSpans} monitor — an unbounded set here would re-create the leak it cleans up.
   */
  private final Set<String> helperEndedSpans = new LinkedHashSet<>();

  /** Fallback defaults used when callers (legacy tests) do not pass a properties bean. */
  private static final OpenTelemetryHelperProperties DEFAULT_PROPERTIES = new OpenTelemetryHelperProperties(
          1000, Duration.ofMinutes(10),
          Duration.ofMinutes(10), 1000);

  public OpenTelemetryHelper(OpenTelemetry openTelemetry,
          OpenTelemetryHelperProperties properties,
          Clock clock) {
    this.tracer = openTelemetry.getTracer(OTEL_INSTRUMENTATION_NAME);
    this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
    this.properties = properties == null ? DEFAULT_PROPERTIES : properties;
    this.clock = clock == null ? Clock.systemUTC() : clock;
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
        case "setbaggage" -> Result.success(setBaggage(ctx, input));
        case "getbaggage" -> Result.success(getBaggage(ctx, input));
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
    Instant now = clock.instant();
    synchronized (openSpans) {
      reapExpiredSpansLocked(now);
      while (openSpans.size() >= properties.spanMaxEntries()) {
        evictOldestSpanLocked(SPAN_EVICTED);
      }
      openSpans.put(spanId, new SpanEntry(span, now));
    }
    return spanId;
  }

  private @NonNull OtelOut endSpan(@NonNull OtelIn input) {
    String spanId = requireSpanId(input, "endSpan");
    String statusCodeRaw = input.statusCode() == null ? "OK" : input.statusCode();
    String statusUpper = statusCodeRaw.toUpperCase(Locale.ROOT);
    if (!STATUS_CODES.contains(statusUpper)) {
      throw new IllegalArgumentException(
              "otel.endSpan: statusCode must be one of OK, ERROR, UNSET, was: " + statusCodeRaw);
    }
    StatusCode status = StatusCode.valueOf(statusUpper);
    String errorMessage = input.errorMessage();
    Instant now = clock.instant();
    SpanEntry removed;
    boolean endedByHelper;
    synchronized (openSpans) {
      reapExpiredSpansLocked(now);
      removed = openSpans.remove(spanId);
      endedByHelper = removed == null && helperEndedSpans.contains(spanId);
    }
    if (removed == null) {
      // Either: (i) caller is ending a span they never started — fail-fast (existing behavior),
      // or (ii) the helper already ended this span itself (TTL/eviction) — silent no-op.
      if (endedByHelper) {
        return OtelOut.ok();
      }
      throw new IllegalArgumentException(
              "otel.endSpan: no open span for spanId '" + spanId
                      + "' (already ended or never started)");
    }
    removed.endOnce(status, errorMessage == null ? "" : errorMessage);
    return OtelOut.ok();
  }

  private @NonNull OtelOut addEvent(@NonNull OtelIn input) {
    String spanId = requireSpanId(input, "addEvent");
    String eventName = input.eventName();
    if (eventName == null || eventName.isBlank()) {
      throw new IllegalArgumentException("otel.addEvent: eventName is required");
    }
    Span span;
    synchronized (openSpans) {
      reapExpiredSpansLocked(clock.instant());
      SpanEntry entry = openSpans.get(spanId);
      span = entry == null ? null : entry.span();
    }
    if (span == null) {
      throw new IllegalArgumentException(
              "otel.addEvent: no open span for spanId '" + spanId
                      + "' (already ended or never started)");
    }
    Map<String, String> attributes = input.attributes() == null ? Map.of() : input.attributes();
    span.addEvent(eventName, toAttributes(attributes));
    return OtelOut.ok();
  }

  private @NonNull OtelOut setBaggage(@NonNull Context<OtelIn> ctx, @NonNull OtelIn input) {
    String key = input.baggageKey();
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("otel.setBaggage: baggageKey is required");
    }
    if (input.baggageValue() == null) {
      throw new IllegalArgumentException("otel.setBaggage: baggageValue is required");
    }
    String runId = ctx.runId();
    String composite = compositeBaggageKey(runId, key);
    Instant now = clock.instant();
    synchronized (baggageStore) {
      reapExpiredBaggageLocked(now);
      enforceBaggageRunBoundLocked(runId, now);
      baggageStore.put(composite, new BaggageEntry(input.baggageValue(), now));
    }
    return OtelOut.ok();
  }

  private @NonNull OtelOut getBaggage(@NonNull Context<OtelIn> ctx, @NonNull OtelIn input) {
    String key = input.baggageKey();
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("otel.getBaggage: baggageKey is required");
    }
    String runId = ctx.runId();
    String composite = compositeBaggageKey(runId, key);
    Instant now = clock.instant();
    BaggageEntry entry;
    // The map is a plain LinkedHashMap mutated under this monitor: the read must hold it too.
    synchronized (baggageStore) {
      reapExpiredBaggageLocked(now);
      entry = baggageStore.get(composite);
    }
    if (entry == null) {
      throw new IllegalArgumentException(
              "otel.getBaggage: no value stored for baggageKey '" + key + "'");
    }
    return OtelOut.ofString(entry.value());
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
    for (Entry<String, String> entry : map.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue() == null ? "" : entry.getValue();
      builder.put(AttributeKey.stringKey(key), value);
    }
    return builder.build();
  }

  /** Caller must hold {@link #openSpans}'s monitor. */
  private void reapExpiredSpansLocked(Instant now) {
    Duration ttl = properties.spanTtl();
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      return;
    }
    Instant cutoff = now.minus(ttl);
    // Iterate the insertion-ordered entry set and stop at the first non-expired entry. Entries are
    // added in monotonic-clock order so a non-expired entry means everything after is also fresh.
    Iterator<Entry<String, SpanEntry>> it = openSpans.entrySet().iterator();
    while (it.hasNext()) {
      Entry<String, SpanEntry> e = it.next();
      if (!e.getValue().startedAt().isBefore(cutoff)) {
        break;
      }
      it.remove();
      e.getValue().endOnce(StatusCode.ERROR, SPAN_ABANDONED);
      rememberHelperEndedLocked(e.getKey());
    }
  }

  /** Caller must hold {@link #openSpans}'s monitor. Keeps the marker set within the span bound. */
  private void rememberHelperEndedLocked(String spanId) {
    helperEndedSpans.add(spanId);
    int max = Math.max(1, properties.spanMaxEntries());
    Iterator<String> it = helperEndedSpans.iterator();
    while (helperEndedSpans.size() > max && it.hasNext()) {
      it.next();
      it.remove();
    }
  }

  /** Test hook: number of remembered helper-ended span ids. */
  int endedMarkerCount() {
    synchronized (openSpans) {
      return helperEndedSpans.size();
    }
  }

  /** Test hook: number of currently open spans. */
  int openSpanCount() {
    synchronized (openSpans) {
      return openSpans.size();
    }
  }

  /** Caller must hold {@link #openSpans}'s monitor. */
  private void evictOldestSpanLocked(String reason) {
    Iterator<Entry<String, SpanEntry>> it = openSpans.entrySet().iterator();
    if (!it.hasNext()) {
      return;
    }
    Entry<String, SpanEntry> oldest = it.next();
    it.remove();
    oldest.getValue().endOnce(StatusCode.ERROR, reason);
    rememberHelperEndedLocked(oldest.getKey());
  }

  /** Caller must hold {@link #baggageStore}'s monitor. */
  private void reapExpiredBaggageLocked(Instant now) {
    Duration ttl = properties.baggageTtl();
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      return;
    }
    Instant cutoff = now.minus(ttl);
    Iterator<Entry<String, BaggageEntry>> it = baggageStore.entrySet().iterator();
    while (it.hasNext()) {
      Entry<String, BaggageEntry> e = it.next();
      if (!e.getValue().setAt().isBefore(cutoff)) {
        break;
      }
      it.remove();
    }
  }

  /**
   * Caller must hold {@link #baggageStore}'s monitor.
   *
   * <p>
   * Bound the number of distinct run-ids tracked. We count runs by walking the map (O(n)) — fine
   * for the configured bound (default 1000) and only invoked on setBaggage.
   */
  private void enforceBaggageRunBoundLocked(String incomingRunId, Instant now) {
    int max = properties.baggageMaxRuns();
    if (max <= 0) {
      return;
    }
    Set<String> runIds = new HashSet<>();
    // LinkedHashMap so the "oldest" pick below is deterministic: when two runs have identical
    // setAt (e.g. concurrent inserts or a paused clock), the first one inserted wins.
    Map<String, Instant> oldestPerRun = new LinkedHashMap<>();
    String sep = String.valueOf(BAGGAGE_KEY_SEPARATOR);
    for (Entry<String, BaggageEntry> e : baggageStore.entrySet()) {
      String runId = e.getKey().split(sep, 2)[0];
      runIds.add(runId);
      Instant candidate = e.getValue().setAt();
      Instant existing = oldestPerRun.get(runId);
      if (existing == null || candidate.isBefore(existing)) {
        oldestPerRun.put(runId, candidate);
      }
    }
    if (runIds.size() < max || runIds.contains(incomingRunId)) {
      return;
    }
    String oldestRun = null;
    Instant oldestAt = null;
    for (Entry<String, Instant> e : oldestPerRun.entrySet()) {
      if (oldestAt == null || e.getValue().isBefore(oldestAt)) {
        oldestRun = e.getKey();
        oldestAt = e.getValue();
      }
    }
    if (oldestRun == null) {
      return;
    }
    String prefix = oldestRun + BAGGAGE_KEY_SEPARATOR;
    Iterator<Entry<String, BaggageEntry>> it = baggageStore.entrySet().iterator();
    while (it.hasNext()) {
      Entry<String, BaggageEntry> e = it.next();
      if (e.getKey().startsWith(prefix)) {
        it.remove();
      }
    }
  }

  private static String compositeBaggageKey(String runId, String bagKey) {
    return runId + BAGGAGE_KEY_SEPARATOR + bagKey;
  }

  private static final class SpanEntry {
    private final Span span;
    private final Instant startedAt;
    private final AtomicBoolean ended = new AtomicBoolean(false);

    SpanEntry(Span span, Instant startedAt) {
      this.span = span;
      this.startedAt = startedAt;
    }

    Span span() {
      return span;
    }

    Instant startedAt() {
      return startedAt;
    }

    /** Calls {@link Span#end()} at most once, even if multiple paths race to end it. */
    void endOnce(StatusCode status, String errorMessage) {
      if (ended.compareAndSet(false, true)) {
        span.setStatus(status, errorMessage == null ? "" : errorMessage);
        span.end();
      }
    }
  }

  private static final class BaggageEntry {
    private final String value;
    private final Instant setAt;

    BaggageEntry(String value, Instant setAt) {
      this.value = value;
      this.setAt = setAt;
    }

    String value() {
      return value;
    }

    Instant setAt() {
      return setAt;
    }
  }
}
