package cbs.nova.starter.vhs.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.config.properties.CbsVhsReplayProperties;
import cbs.nova.starter.core.event.DslExecutionEvent.DslExternalCallEvent;
import cbs.nova.starter.core.event.DslExecutionEvent.DslRunCompletedEvent;
import cbs.nova.starter.core.event.DslExecutionEvent.DslRunStartedEvent;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.vhs.TapeEvent;
import cbs.nova.starter.vhs.VhsRecorder;
import cbs.nova.starter.vhs.VhsTapeSink;
import cbs.nova.starter.vhs.VhsTapeWriter;
import cbs.nova.starter.vhs.replay.VhsCallDriver.CallResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests for the VHS replay engine.
 *
 * <p>
 * Fixture tapes are recorded through T555's real recorder stack ({@link VhsRecorder} +
 * {@link VhsTapeWriter}), never hand-authored; only parser-focused negative cases corrupt lines on
 * purpose.
 */
class VhsReplayEngineTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @TempDir
  Path tempDir;

  // ---------------------------------------------------------------------------------------------
  // Fixture tape generation (via T555's real recorder/writer stack)
  // ---------------------------------------------------------------------------------------------

  record RecordedCall(String type, String target, String operation, Object input, long gapMs) {
  }

  /**
   * Records a real tape through {@link VhsRecorder} + {@link VhsTapeWriter}. Real sleeps between
   * events give the tape deterministic {@code relative_ms} deltas (the recorder stamps wall-clock
   * time).
   */
  private Path recordTape(String runId, List<RecordedCall> calls) throws Exception {
    Path file = tempDir.resolve("recorded_" + runId + ".vhs.jsonl");
    SingleFileTapeSink sink = new SingleFileTapeSink(file,
            new FakeClock(Instant.parse("2026-01-01T00:00:00Z")));
    VhsRecorder recorder = new VhsRecorder(sink, List.of("*"));
    DslExecutionEventBus bus = new DslExecutionEventBus();
    bus.register(recorder);

    bus.publish(new DslRunStartedEvent(runId, "Ping", ExecutionMode.RUN, "corr-1"));
    for (RecordedCall call : calls) {
      Thread.sleep(call.gapMs);
      bus.publish(new DslExternalCallEvent(
              runId, call.type, call.target, call.operation, call.input));
    }
    Thread.sleep(5);
    bus.publish(new DslRunCompletedEvent(
            runId, "Ping", ExecutionMode.RUN, Result.success("done"), "corr-1"));
    return file;
  }

  /** Writes a valid tape through the real {@link VhsTapeWriter} (header + trailer included). */
  private Path writeTapeWithWriter(String name, List<TapeEvent> events) throws Exception {
    Path file = tempDir.resolve(name);
    FakeClock clock = new FakeClock(Instant.parse("2026-01-01T00:00:00Z"));
    VhsTapeWriter writer = new VhsTapeWriter(file, "run-writer", "corr-w", "Ping",
            clock.now(), MAPPER, clock);
    for (TapeEvent event : events) {
      writer.append(event);
    }
    writer.close();
    return file;
  }

  private static TapeEvent callStart(int index, long relativeMs, String callId, Object input) {
    return new TapeEvent(
            "1", index, "call_start", "2026-01-01T00:00:00Z", relativeMs,
            new TapeEvent.CallMetadata(callId, "process", "PingProcess", "run"),
            input, null,
            new TapeEvent.Timing(null, null, null), null, Map.of());
  }

  private static TapeEvent runStarted(int index, long relativeMs) {
    return new TapeEvent(
            "1", index, "run_started", "2026-01-01T00:00:00Z", relativeMs,
            null, null, null,
            new TapeEvent.Timing(null, null, null), null, Map.of());
  }

  // ---------------------------------------------------------------------------------------------
  // Test doubles
  // ---------------------------------------------------------------------------------------------

  /** Records requested sleeps instead of actually sleeping. */
  static final class RecordingSleeper implements VhsReplayEngine.Sleeper {
    final List<Long> sleeps = new ArrayList<>();

    @Override
    public void sleep(long millis) {
      sleeps.add(millis);
    }

    long total() {
      return sleeps.stream().mapToLong(Long::longValue).sum();
    }
  }

  /** Records executed calls; can be told to fail a specific call id. */
  static final class RecordingDriver implements VhsCallDriver {
    final List<String> callIds = new CopyOnWriteArrayList<>();
    final AtomicInteger invocations = new AtomicInteger();
    volatile String failOnCallId;

    @Override
    public CallResult execute(TapeEvent callStartEvent) {
      invocations.incrementAndGet();
      String callId = callStartEvent.callMetadata().callId();
      callIds.add(callId);
      if (callId.equals(failOnCallId)) {
        return CallResult.failure("boom: driver instructed to fail " + callId);
      }
      return CallResult.success(Map.of("echo", callStartEvent.input()));
    }
  }

  /** Blocks all calls until released, to measure the concurrency cap. */
  static final class BlockingDriver implements VhsCallDriver {
    final CountDownLatch release = new CountDownLatch(1);
    final AtomicInteger inFlight = new AtomicInteger();
    final AtomicInteger maxInFlight = new AtomicInteger();

    @Override
    public CallResult execute(TapeEvent callStartEvent) {
      int now = inFlight.incrementAndGet();
      maxInFlight.accumulateAndGet(now, Math::max);
      try {
        release.await(10, TimeUnit.SECONDS);
        return CallResult.success(Map.of());
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return CallResult.failure("interrupted");
      } finally {
        inFlight.decrementAndGet();
      }
    }
  }

  static final class FakeClock implements VhsTapeWriter.InstantSource {
    private Instant now;

    FakeClock(Instant start) {
      this.now = start;
    }

    void advance(long millis) {
      now = now.plusMillis(millis);
    }

    @Override
    public Instant now() {
      return now;
    }
  }

  /** Tape sink that routes one run to one {@link VhsTapeWriter} file. */
  static final class SingleFileTapeSink implements VhsTapeSink {
    private final Path file;
    private final FakeClock clock;
    private VhsTapeWriter writer;

    SingleFileTapeSink(Path file, FakeClock clock) {
      this.file = file;
      this.clock = clock;
    }

    @Override
    public void start(String runId, String route, String correlationId) {
      if (writer == null) {
        writer = new VhsTapeWriter(file, runId, correlationId, route, clock.now(),
                JsonMapper.builder().build(), clock);
      }
    }

    @Override
    public void append(String runId, TapeEvent event) {
      writer.append(event);
    }

    @Override
    public long close(String runId) {
      return writer.close();
    }
  }

  private static CbsVhsReplayProperties props() {
    return props(false);
  }

  private static CbsVhsReplayProperties props(boolean compareOutput) {
    return new CbsVhsReplayProperties(false, "dry-run", false,
            ReplayMode.exact, 1, 1.0, 4, 0, compareOutput, 0, 30000, "http://localhost:8080");
  }

  private VhsReplayEngine engine(VhsCallDriver driver, VhsReplayEngine.Sleeper sleeper) {
    return new VhsReplayEngine(props(), driver, new VhsTapeReader(MAPPER), sleeper);
  }

  // ---------------------------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------------------------

  @Test
  void exactReplayReproducesCallOrderAndTimingDeltas() throws Exception {
    Path tape = recordTape("run-order", List.of(
            new RecordedCall("process", "PingProcess", "run", Map.of("n", 1), 100),
            new RecordedCall("process", "PingProcess", "run", Map.of("n", 2), 150)));

    RecordingDriver driver = new RecordingDriver();
    RecordingSleeper sleeper = new RecordingSleeper();
    VhsReplayReport report = engine(driver, sleeper).replayExact(tape);

    assertThat(driver.callIds).containsExactly("call_001", "call_002");

    // Waits must reproduce the timing deltas actually recorded on the tape.
    List<Long> recordedDeltas = recordedCallDeltas(tape);
    assertThat(recordedDeltas).hasSize(2);
    assertThat(sleeper.sleeps).containsExactlyElementsOf(recordedDeltas);
    assertThat(report.mode()).isEqualTo(ReplayMode.exact);
    assertThat(report.successfulCalls()).isEqualTo(2);
    assertThat(report.failedCalls()).isZero();
    assertThat(report.tapes()).hasSize(1);
    assertThat(report.tapes().get(0).isSuccess()).isTrue();
  }

  @Test
  void exactReplayTimingStaysWithinToleranceWithRealSleeper() throws Exception {
    Path tape = recordTape("run-realtime", List.of(
            new RecordedCall("process", "PingProcess", "run", Map.of("n", 1), 40),
            new RecordedCall("process", "PingProcess", "run", Map.of("n", 2), 60)));

    long start = System.nanoTime();
    VhsReplayReport report = engine(new RecordingDriver(), millis -> Thread.sleep(millis))
            .replayExact(tape);
    long wallMs = (System.nanoTime() - start) / 1_000_000;

    // original relative span is ~100ms; generous scheduling slack allowed
    assertThat(wallMs).isGreaterThanOrEqualTo(90);
    assertThat(wallMs).isLessThan(10_000);
    assertThat(report.successfulCalls()).isEqualTo(2);
  }

  @Test
  void loadReplaySpeedOneMatchesExactReplayObservations() throws Exception {
    Path tape = recordTape("run-equiv", List.of(
            new RecordedCall("process", "PingProcess", "run", Map.of("n", 1), 100),
            new RecordedCall("process", "PingProcess", "run", Map.of("n", 2), 150)));

    RecordingDriver exactDriver = new RecordingDriver();
    RecordingSleeper exactSleeper = new RecordingSleeper();
    VhsReplayReport exact = engine(exactDriver, exactSleeper).replayExact(tape);

    RecordingDriver loadDriver = new RecordingDriver();
    RecordingSleeper loadSleeper = new RecordingSleeper();
    VhsReplayReport load = engine(loadDriver, loadSleeper).replayLoad(tape, 1, 1.0, 1);

    assertThat(loadDriver.callIds).containsExactlyElementsOf(exactDriver.callIds);
    assertThat(loadSleeper.sleeps).containsExactlyElementsOf(exactSleeper.sleeps);
    assertThat(load.successfulCalls()).isEqualTo(exact.successfulCalls());
    assertThat(load.failedCalls()).isEqualTo(exact.failedCalls());
  }

  @Test
  void loadReplaySpeedTwoHalvesWaitsAndRespectsConcurrencyCap() throws Exception {
    Path tape = recordTape("run-speed", List.of(
            new RecordedCall("process", "PingProcess", "run", Map.of("n", 1), 100),
            new RecordedCall("process", "PingProcess", "run", Map.of("n", 2), 150)));

    RecordingSleeper speedOneSleeper = new RecordingSleeper();
    new VhsReplayEngine(props(), new RecordingDriver(), new VhsTapeReader(MAPPER),
            speedOneSleeper).replayLoad(tape, 1, 1.0, 1);

    RecordingSleeper speedTwoSleeper = new RecordingSleeper();
    int copies = 4;
    VhsReplayReport report = new VhsReplayEngine(props(), new RecordingDriver(),
            new VhsTapeReader(MAPPER), speedTwoSleeper).replayLoad(tape, copies, 2.0, 4);

    long oneTotal = speedOneSleeper.total();
    long twoTotalPerCopy = speedTwoSleeper.total() / copies;
    assertThat(oneTotal).isGreaterThanOrEqualTo(200);
    assertThat((double) twoTotalPerCopy).isCloseTo(oneTotal / 2.0,
            org.assertj.core.data.Offset.offset(oneTotal * 0.3));
    assertThat(report.tapeCount()).isEqualTo(copies);
    assertThat(report.successfulCalls()).isEqualTo(2L * copies);
    assertThat(report.skippedCopies()).isZero();

    // concurrency cap: 6 copies at speed 1000 with cap 2 -> at most 2 calls in flight
    BlockingDriver blocking = new BlockingDriver();
    VhsReplayEngine blockingEngine = new VhsReplayEngine(props(), blocking);
    CompletableFuture<Void> future = CompletableFuture
            .runAsync(() -> blockingEngine.replayLoad(tape, 6, 1000.0, 2));
    awaitTrue(() -> blocking.inFlight.get() == 2);
    assertThat(blocking.maxInFlight.get()).isLessThanOrEqualTo(2);
    blocking.release.countDown();
    future.join();
    assertThat(blocking.maxInFlight.get()).isLessThanOrEqualTo(2);
  }

  @Test
  void malformedTapesFailBeforeAnyCallExecutes() throws Exception {
    Path valid = recordTape("run-valid", List.of(
            new RecordedCall("process", "PingProcess", "run", Map.of("n", 1), 10)));
    List<String> original = Files.readAllLines(valid);

    List<Path> corrupted = new ArrayList<>();
    corrupted.add(rewrite("bad-format", original, 0,
            line -> line.replace("\"1.0.0\"", "\"2.0.0\"")));
    corrupted.add(rewrite("bad-schema", original, 0,
            line -> line.replace("\"schema_version\":\"1\"", "\"schema_version\":\"99\"")));
    corrupted.add(rewrite("no-trailer", original, -1, line -> null));
    corrupted.add(rewrite("bad-index", original, 2,
            line -> line.replace("\"event_index\":1", "\"event_index\":7")));
    corrupted.add(rewrite("bad-count", original, original.size() - 1,
            line -> line.replaceAll("(\"event_count\":)\\d+", "$1999")));

    for (Path bad : corrupted) {
      RecordingDriver driver = new RecordingDriver();
      VhsReplayEngine engine = new VhsReplayEngine(props(), driver);
      assertThatThrownBy(() -> engine.replayExact(bad))
              .isInstanceOf(VhsReplayException.class)
              .hasMessageContaining("VHS tape");
      assertThat(driver.invocations.get())
              .as("no call may execute on malformed tape %s", bad.getFileName())
              .isZero();
    }
  }

  @Test
  void readerRejectsEventSchemaMismatchAgainstHeader() throws Exception {
    Path tape = writeTapeWithWriter("schema-mismatch.vhs.jsonl", List.of(
            runStarted(0, 0),
            new TapeEvent(
                    "2", 1, "call_start", "2026-01-01T00:00:00Z", 0,
                    new TapeEvent.CallMetadata("call_001", "process", "P", "run"),
                    Map.of(), null, new TapeEvent.Timing(null, null, null), null, Map.of())));
    assertThatThrownBy(() -> new VhsTapeReader(MAPPER).read(tape))
            .isInstanceOf(VhsReplayException.class)
            .hasMessageContaining("schema_version");
  }

  @Test
  void readerRoundTripsRecorderProducedTape() throws Exception {
    Path tape = recordTape("run-roundtrip", List.of(
            new RecordedCall("helper", "FetchHelper", "get", Map.of("id", 42), 5)));
    VhsTapeReader.VhsTape parsed = new VhsTapeReader(MAPPER).read(tape);
    assertThat(parsed.header().sourceRunId()).isEqualTo("run-roundtrip");
    assertThat(parsed.events()).extracting(TapeEvent::eventType)
            .containsExactly("run_started", "call_start", "call_end", "run_completed");
  }

  @Test
  void defaultTargetIsDryRunAndProductionRequiresBothOptInKeys() {
    assertThat(VhsCallDrivers.resolve(props(), MAPPER, null))
            .isInstanceOf(DryRunCallDriver.class);

    CbsVhsReplayProperties prodConfigOnly = new CbsVhsReplayProperties(false,
            "https://prod.example.com", true, ReplayMode.exact, 1, 1.0, 4, 0, false, 0,
            30000, "http://localhost:8080");
    assertThatThrownBy(() -> VhsCallDrivers.resolve(prodConfigOnly, MAPPER, null))
            .isInstanceOf(VhsReplayException.class)
            .hasMessageContaining("Refusing to replay against production-like target")
            .hasMessageContaining(VhsCallDrivers.PRODUCTION_ENV_KEY);

    CbsVhsReplayProperties prodEnvOnly = new CbsVhsReplayProperties(false,
            "https://prod.example.com", false, ReplayMode.exact, 1, 1.0, 4, 0, false, 0,
            30000, "http://localhost:8080");
    assertThatThrownBy(() -> VhsCallDrivers.resolve(prodEnvOnly, MAPPER, "1"))
            .isInstanceOf(VhsReplayException.class)
            .hasMessageContaining("Refusing");

    assertThat(VhsCallDrivers.resolve(prodConfigOnly, MAPPER, "1"))
            .isInstanceOf(LocalBackendCallDriver.class);

    CbsVhsReplayProperties local = new CbsVhsReplayProperties(false, "local", false,
            ReplayMode.exact, 1, 1.0, 4, 0, false, 0, 30000, "http://localhost:8080");
    assertThat(VhsCallDrivers.resolve(local, MAPPER, null))
            .isInstanceOf(LocalBackendCallDriver.class);
  }

  @Test
  void failingCallStopsTapeAndIsRecordedInReport() throws Exception {
    Path tape = recordTape("run-fail", List.of(
            new RecordedCall("process", "PingProcess", "run", Map.of("n", 1), 10),
            new RecordedCall("process", "PingProcess", "run", Map.of("n", 2), 10)));
    RecordingDriver driver = new RecordingDriver();
    driver.failOnCallId = "call_001";

    VhsReplayReport report = engine(driver, new RecordingSleeper()).replayExact(tape);

    assertThat(report.failedCalls()).isEqualTo(1);
    assertThat(report.successfulCalls()).isZero();
    assertThat(report.tapes().get(0).isSuccess()).isFalse();
    assertThat(report.observations()).hasSize(1);
    assertThat(report.observations().get(0).error()).contains("boom");
    // tape aborted after first failure: second call never executed
    assertThat(driver.callIds).containsExactly("call_001");
  }

  @Test
  void outputMismatchIsReportedNotFatalWhenComparisonEnabled() throws Exception {
    Path tape = writeTapeWithWriter("compare.vhs.jsonl", List.of(
            runStarted(0, 0),
            callStart(1, 0, "call_001", Map.of("n", 1)),
            new TapeEvent(
                    "1", 2, "call_end", "2026-01-01T00:00:00Z", 5,
                    new TapeEvent.CallMetadata("call_001", "process", "PingProcess", "run"),
                    null, "recorded-output", new TapeEvent.Timing(null, null, 5L), null,
                    Map.of())));

    // driver echoes the input back, which does not match "recorded-output"
    VhsReplayReport compared = new VhsReplayEngine(props(true), new RecordingDriver(),
            new VhsTapeReader(MAPPER), new RecordingSleeper()).replayExact(tape);
    assertThat(compared.failedCalls()).isZero();
    assertThat(compared.observations()).hasSize(1);
    assertThat(compared.observations().get(0).outputCompared()).isTrue();
    assertThat(compared.observations().get(0).outputMismatch()).isTrue();

    VhsReplayReport unCompared = new VhsReplayEngine(props(false), new RecordingDriver(),
            new VhsTapeReader(MAPPER), new RecordingSleeper()).replayExact(tape);
    assertThat(unCompared.observations().get(0).outputCompared()).isFalse();
  }

  @Test
  void reportContainsPerTapeCountsAndLatencyObservations() throws Exception {
    Path tape = recordTape("run-report", List.of(
            new RecordedCall("process", "PingProcess", "run", Map.of("n", 1), 10),
            new RecordedCall("process", "PingProcess", "run", Map.of("n", 2), 10)));
    VhsReplayReport report = engine(new RecordingDriver(), new RecordingSleeper())
            .replayLoad(tape, 3, 1.0, 2);

    assertThat(report.tapeCount()).isEqualTo(3);
    assertThat(report.totalCalls()).isEqualTo(6);
    assertThat(report.successfulCalls()).isEqualTo(6);
    assertThat(report.failedCalls()).isZero();
    assertThat(report.observations()).hasSize(6)
            .allSatisfy(obs -> assertThat(obs.latencyMs()).isGreaterThanOrEqualTo(0));
    assertThat(report.tapes()).allMatch(VhsReplayReport.TapeSummary::isSuccess);
  }

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

  private Path rewrite(String name, List<String> lines, int lineIndex,
          Function<String, String> edit) throws Exception {
    List<String> copy = new ArrayList<>(lines);
    int idx = lineIndex < 0 ? copy.size() + lineIndex : lineIndex;
    String edited = edit.apply(copy.get(idx));
    if (edited == null) {
      copy.remove(idx);
    } else {
      copy.set(idx, edited);
    }
    Path out = tempDir.resolve(name + ".vhs.jsonl");
    Files.write(out, copy);
    return out;
  }

  /** Extracts the {@code relative_ms} deltas between consecutive {@code call_start} events. */
  private static List<Long> recordedCallDeltas(Path tape) {
    List<cbs.nova.starter.vhs.TapeEvent> calls = new VhsTapeReader(MAPPER).read(tape).events()
            .stream().filter(e -> "call_start".equals(e.eventType())).toList();
    List<Long> deltas = new ArrayList<>();
    long previous = 0;
    for (cbs.nova.starter.vhs.TapeEvent call : calls) {
      deltas.add(call.relativeMs() - previous);
      previous = call.relativeMs();
    }
    return deltas;
  }

  private static void awaitTrue(BooleanSupplier condition) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      try {
        Thread.sleep(5);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }
}
