package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.config.properties.OpenTelemetryHelperProperties;
import cbs.nova.starter.helper.model.OtelIn;
import cbs.nova.starter.helper.model.OtelOut;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

  private MutableClock mutableClock;
  private OpenTelemetryHelper helper;

  @BeforeEach
  void setUp() {
    mutableClock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
    helper = new OpenTelemetryHelper(
            OTEL.getOpenTelemetry(),
            new OpenTelemetryHelperProperties(
                    /* spanMaxEntries */ 1000,
                    Duration.ofMinutes(10),
                    Duration.ofMinutes(10),
                    /* baggageMaxRuns */ 1000),
            mutableClock);
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

  // ----- Bounded-span-state lifecycle tests (T789) -----

  @Test
  void leakedSpanIsEndedWithAbandonedAfterTtlPasses() {
    String traceparent = (String) execute(OtelInSpan("leaked")).value().result();

    // No endSpan call — simulate a run that crashed mid-flight. Span stays open past TTL,
    // next helper call must end it with ERROR/"abandoned" via lazy cleanup.
    mutableClock.advance(Duration.ofMinutes(10).plusSeconds(1));

    String trigger = (String) execute(OtelInSpan("next")).value().result();
    // End the trigger so it lands in OTEL.getSpans().
    Result<OtelOut> end = execute(new OtelIn(
            "endSpan", null, null, trigger, "OK", null, null, null, null, null));
    assertThat(end.isSuccess()).isTrue();

    List<SpanData> finished = OTEL.getSpans();
    SpanData leaked = finished.stream()
            .filter(d -> d.getName().equals("leaked"))
            .findFirst().orElseThrow();
    assertThat(leaked.getStatus().getStatusCode())
            .isEqualTo(io.opentelemetry.sdk.trace.data.StatusData.error().getStatusCode());
    assertThat(leaked.getStatus().getDescription()).isEqualTo("abandoned");
    assertThat(leaked.hasEnded()).isTrue();
  }

  @Test
  void leakedSpanTtlBoundaryKeepsItOpen() {
    String traceparent = (String) execute(OtelInSpan("leaked")).value().result();

    // Just before TTL — still open, not ended.
    mutableClock.advance(Duration.ofMinutes(10).minusSeconds(1));
    // A new helper call should NOT reap the leaked span; if it did, the leaked span would be
    // ended with ERROR/"abandoned". Verify the opposite: the span is still open by adding an
    // event to it (addEvent rejects ended spans).
    Result<OtelOut> addOnLeaked = execute(new OtelIn(
            "addEvent", null, null, traceparent, null, null, "still-alive", null, null, null));
    assertThat(addOnLeaked.isSuccess()).isTrue();
  }

  @Test
  void endSpanAfterTtlCleanupIsSilentNoOp() {
    String traceparent = (String) execute(OtelInSpan("leaked")).value().result();
    mutableClock.advance(Duration.ofMinutes(11));

    // Trigger TTL cleanup
    execute(OtelInSpan("next"));

    // Late endSpan should NOT throw, NOT double-end.
    Result<OtelOut> late = execute(new OtelIn(
            "endSpan", null, null, traceparent, "OK", null, null, null, null, null));
    assertThat(late.isSuccess()).isTrue();

    SpanData leaked = OTEL.getSpans().stream()
            .filter(d -> d.getName().equals("leaked"))
            .findFirst().orElseThrow();
    // Ended exactly once with the abandoned marker, not the caller-supplied OK.
    assertThat(leaked.getStatus().getDescription()).isEqualTo("abandoned");
    assertThat(leaked.hasEnded()).isTrue();
  }

  @Test
  void maxEntriesEvictionEndsOldestWithEvictedMarker() {
    helper = new OpenTelemetryHelper(
            OTEL.getOpenTelemetry(),
            new OpenTelemetryHelperProperties(
                    /* spanMaxEntries */ 2,
                    Duration.ofMinutes(10),
                    Duration.ofMinutes(10),
                    1000),
            mutableClock);

    String tpA = (String) execute(OtelInSpan("a")).value().result();
    String tpB = (String) execute(OtelInSpan("b")).value().result();
    String tpC = (String) execute(OtelInSpan("c")).value().result();
    // Inserting b must NOT evict a (size was 1); inserting c must evict a (oldest, size was 2).
    // End c so its SpanData is visible in OTEL.getSpans().
    execute(new OtelIn(
            "endSpan", null, null, tpC, "OK", null, null, null, null, null));

    // a should have been ended with ERROR/"evicted".
    List<SpanData> finished = OTEL.getSpans();
    SpanData a = finished.stream()
            .filter(d -> d.getName().equals("a"))
            .findFirst().orElseThrow();
    assertThat(a.hasEnded()).isTrue();
    assertThat(a.getStatus().getDescription()).isEqualTo("evicted");

    // b is still open (addEvent works).
    Result<OtelOut> addOnB = execute(new OtelIn(
            "addEvent", null, null, tpB, null, null, "evt", null, null, null));
    assertThat(addOnB.isSuccess()).isTrue();
    assertThat(tpA).isNotNull();
    assertThat(tpC).isNotNull();
  }

  @Test
  void endSpanAfterEvictionIsSilentNoOp() {
    helper = new OpenTelemetryHelper(
            OTEL.getOpenTelemetry(),
            new OpenTelemetryHelperProperties(
                    /* spanMaxEntries */ 1,
                    Duration.ofMinutes(10),
                    Duration.ofMinutes(10),
                    1000),
            mutableClock);

    String tpA = (String) execute(OtelInSpan("a")).value().result();
    // Insert b → evicts a with ERROR/"evicted".
    execute(OtelInSpan("b"));

    // Late endSpan on tpA: no-op success, span already ended with "evicted".
    Result<OtelOut> late = execute(new OtelIn(
            "endSpan", null, null, tpA, "OK", null, null, null, null, null));
    assertThat(late.isSuccess()).isTrue();

    SpanData a = OTEL.getSpans().stream()
            .filter(d -> d.getName().equals("a"))
            .findFirst().orElseThrow();
    assertThat(a.hasEnded()).isTrue();
    assertThat(a.getStatus().getDescription()).isEqualTo("evicted");
  }

  @Test
  void spanEndIsInvokedAtMostOnceUnderConcurrency() throws Exception {
    String tp = (String) execute(OtelInSpan("race")).value().result();

    int threads = 8;
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
    java.util.concurrent.atomic.AtomicInteger errors = new java.util.concurrent.atomic.AtomicInteger(
            0);
    Runnable r = () -> {
      try {
        start.await();
        Result<OtelOut> res = execute(new OtelIn(
                "endSpan", null, null, tp, "OK", null, null, null, null, null));
        if (!res.isSuccess()) {
          errors.incrementAndGet();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        done.countDown();
      }
    };
    for (int i = 0; i < threads; i++) {
      new Thread(r).start();
    }
    start.countDown();
    done.await();

    // Exactly one thread should observe a successful end; the other seven should fail-fast with
    // "no open span". Span must have ended exactly once.
    assertThat(errors.get()).isEqualTo(threads - 1);
    SpanData d = OTEL.getSpans().stream()
            .filter(s -> s.getName().equals("race"))
            .findFirst().orElseThrow();
    assertThat(d.hasEnded()).isTrue();
  }

  // ----- Per-run baggage scoping tests (T789) -----

  @Test
  void baggageSetInRunAIsNotVisibleInRunB() {
    String runA = "run-a";
    String runB = "run-b";

    Result<OtelOut> setInA = executeForRun(runA, new OtelIn(
            "setBaggage", null, null, null, null, null, null, "user", "alice", null));
    assertThat(setInA.isSuccess()).isTrue();

    Result<OtelOut> getInB = executeForRun(runB, new OtelIn(
            "getBaggage", null, null, null, null, null, null, "user", null, null));
    assertThat(getInB.isSuccess()).isFalse();
    assertThat(getInB.cause()).hasMessageContaining("no value stored for baggageKey");
  }

  @Test
  void baggageInSameRunIsVisible() {
    String run = "same-run";
    executeForRun(run, new OtelIn(
            "setBaggage", null, null, null, null, null, null, "user", "bob", null));
    Result<OtelOut> get = executeForRun(run, new OtelIn(
            "getBaggage", null, null, null, null, null, null, "user", null, null));
    assertThat(get.isSuccess()).isTrue();
    assertThat(get.value().result()).isEqualTo("bob");
  }

  @Test
  void baggageTtlDropsEntryOnNextAccess() {
    String run = "ttl-baggage";
    executeForRun(run, new OtelIn(
            "setBaggage", null, null, null, null, null, null, "u", "x", null));
    mutableClock.advance(Duration.ofMinutes(10).plusSeconds(1));
    Result<OtelOut> get = executeForRun(run, new OtelIn(
            "getBaggage", null, null, null, null, null, null, "u", null, null));
    assertThat(get.isSuccess()).isFalse();
    assertThat(get.cause()).hasMessageContaining("no value stored for baggageKey");
  }

  @Test
  void baggageMaxRunsEvictsOldestRunSilently() {
    helper = new OpenTelemetryHelper(
            OTEL.getOpenTelemetry(),
            new OpenTelemetryHelperProperties(
                    1000,
                    Duration.ofMinutes(10),
                    Duration.ofMinutes(10),
                    /* baggageMaxRuns */ 2),
            mutableClock);

    executeForRun("r1", new OtelIn(
            "setBaggage", null, null, null, null, null, null, "k", "v1", null));
    executeForRun("r2", new OtelIn(
            "setBaggage", null, null, null, null, null, null, "k", "v2", null));
    executeForRun("r3", new OtelIn(
            "setBaggage", null, null, null, null, null, null, "k", "v3", null));

    // r1's entries were evicted silently when r3 was inserted (r1 is the oldest by insertion
    // order).
    Result<OtelOut> getFromR1 = executeForRun("r1", new OtelIn(
            "getBaggage", null, null, null, null, null, null, "k", null, null));
    assertThat(getFromR1.isSuccess()).isFalse();

    Result<OtelOut> getFromR3 = executeForRun("r3", new OtelIn(
            "getBaggage", null, null, null, null, null, null, "k", null, null));
    assertThat(getFromR3.isSuccess()).isTrue();
    assertThat(getFromR3.value().result()).isEqualTo("v3");
  }

  @Test
  void helperEndedMarkersStayBoundedWhenManySpansLeak() {
    int max = 5;
    helper = new OpenTelemetryHelper(
            OTEL.getOpenTelemetry(),
            new OpenTelemetryHelperProperties(max, Duration.ofMinutes(10), Duration.ofMinutes(10),
                    1000),
            mutableClock);

    // Leak far more spans than the bound; each overflow evicts (and remembers) the oldest.
    for (int i = 0; i < max * 20; i++) {
      execute(OtelInSpan("leak-" + i));
    }

    assertThat(helper.openSpanCount()).isLessThanOrEqualTo(max);
    assertThat(helper.endedMarkerCount())
            .as("the remembered-ended-id set must not grow with every leaked span")
            .isLessThanOrEqualTo(max);

    // Same for TTL reaping: advance past the TTL so all open spans are reaped on the next call.
    mutableClock.advance(Duration.ofMinutes(11));
    for (int i = 0; i < max * 20; i++) {
      execute(OtelInSpan("later-" + i));
      mutableClock.advance(Duration.ofMinutes(11));
    }
    assertThat(helper.endedMarkerCount()).isLessThanOrEqualTo(max);
  }

  @Test
  void concurrentBaggageReadsAndWritesNeverFailOrCorrupt() throws Exception {
    int threads = 8;
    int iterations = 400;
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors
            .newFixedThreadPool(threads);
    java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
    for (int t = 0; t < threads; t++) {
      String run = "run-" + t;
      futures.add(pool.submit(() -> {
        start.await();
        for (int i = 0; i < iterations; i++) {
          String key = "k" + (i % 7);
          executeForRun(run, new OtelIn(
                  "setBaggage", null, null, null, null, null, null, key, "v" + i, null));
          Result<OtelOut> got = executeForRun(run, new OtelIn(
                  "getBaggage", null, null, null, null, null, null, key, null, null));
          // Own write must be readable (runs are isolated; no other thread writes this run).
          assertThat(got.isSuccess()).isTrue();
        }
        return null;
      }));
    }
    start.countDown();
    for (java.util.concurrent.Future<?> f : futures) {
      f.get(30, java.util.concurrent.TimeUnit.SECONDS);
    }
    pool.shutdownNow();
  }

  private static final String DEFAULT_RUN_ID = "default-run";

  private Result<OtelOut> execute(OtelIn input) {
    return helper.execute(SimpleContext.builder(input)
            .mode(ExecutionMode.PREVIEW)
            .runId(DEFAULT_RUN_ID)
            .build());
  }

  private Result<OtelOut> executeForRun(String runId, OtelIn input) {
    return helper.execute(SimpleContext.builder(input)
            .mode(ExecutionMode.PREVIEW)
            .runId(runId)
            .build());
  }

  private static OtelIn OtelInSpan(String name) {
    return new OtelIn("span", name, null, null, null, null, null, null, null, null);
  }

  /** Mutable {@link Clock} that returns an {@link Instant} we can advance in tests. */
  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant initial) {
      this.now = initial;
    }

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
