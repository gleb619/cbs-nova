package cbs.nova.starter.vhs.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.CbsVhsReplayProperties;
import cbs.nova.starter.vhs.replay.ReplayMode;
import cbs.nova.starter.vhs.replay.VhsReplayReport;
import cbs.nova.starter.vhs.replay.VhsTapeReader;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class VhsLoadTestTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @TempDir
  Path tempDir;

  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
  }

  // ---------------------------------------------------------------------------------------------
  // Fixture tape generation (same approach as VhsReplayEngineTest)
  // ---------------------------------------------------------------------------------------------

  private Path writeTape(String name, List<cbs.nova.starter.vhs.TapeEvent> events)
          throws Exception {
    Path file = tempDir.resolve(name);
    FakeClock clock = new FakeClock(Instant.parse("2026-01-01T00:00:00Z"));
    cbs.nova.starter.vhs.VhsTapeWriter writer = new cbs.nova.starter.vhs.VhsTapeWriter(
            file, "run-" + name.replace(".vhs.jsonl", ""), "corr-" + name.replace(".vhs.jsonl", ""),
            "Ping", clock.now(), MAPPER, clock);
    for (cbs.nova.starter.vhs.TapeEvent event : events) {
      writer.append(event);
    }
    writer.close();
    return file;
  }

  private static cbs.nova.starter.vhs.TapeEvent runStarted(int index, long relativeMs) {
    return new cbs.nova.starter.vhs.TapeEvent(
            "1", index, "run_started", "2026-01-01T00:00:00Z", relativeMs,
            null, null, null,
            new cbs.nova.starter.vhs.TapeEvent.Timing(null, null, null), null, Map.of());
  }

  private static cbs.nova.starter.vhs.TapeEvent callStart(int index, long relativeMs,
          String callId, Object input) {
    return new cbs.nova.starter.vhs.TapeEvent(
            "1", index, "call_start", "2026-01-01T00:00:00Z", relativeMs,
            new cbs.nova.starter.vhs.TapeEvent.CallMetadata(callId, "process", "PingProcess",
                    "run"),
            input, null,
            new cbs.nova.starter.vhs.TapeEvent.Timing(null, null, null), null, Map.of());
  }

  private static cbs.nova.starter.vhs.TapeEvent callEnd(int index, long relativeMs,
          String callId) {
    return new cbs.nova.starter.vhs.TapeEvent(
            "1", index, "call_end", "2026-01-01T00:00:00Z", relativeMs,
            new cbs.nova.starter.vhs.TapeEvent.CallMetadata(callId, "process", "PingProcess",
                    "run"),
            null, Map.of("echo", true),
            new cbs.nova.starter.vhs.TapeEvent.Timing("2026-01-01T00:00:00Z",
                    "2026-01-01T00:00:00.200Z", 200L),
            null, Map.of());
  }

  private static cbs.nova.starter.vhs.TapeEvent runCompleted(int index, long relativeMs) {
    return new cbs.nova.starter.vhs.TapeEvent(
            "1", index, "run_completed", "2026-01-01T00:00:00Z", relativeMs,
            null, null, Map.of("status", "OK"),
            new cbs.nova.starter.vhs.TapeEvent.Timing("2026-01-01T00:00:00Z",
                    "2026-01-01T00:00:01.000Z", 1000L),
            null, Map.of());
  }

  private CbsVhsReplayProperties testProps() {
    return new CbsVhsReplayProperties(
            true, "dry-run", false, ReplayMode.load, 2, 2.0, 4, 0, false, 0,
            30000, "http://localhost:8080",
            CbsVhsReplayProperties.Faking.disabled());
  }

  // ---------------------------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------------------------

  @Test
  void loadTestRunsAgainstDryRunAndPublishesMetrics() throws Exception {
    writeTape("tape_a.vhs.jsonl", List.of(
            runStarted(0, 0),
            callStart(1, 0, "call_001", Map.of("n", 1)),
            callEnd(2, 200, "call_001"),
            runCompleted(3, 1000)));

    writeTape("tape_b.vhs.jsonl", List.of(
            runStarted(0, 0),
            callStart(1, 0, "call_001", Map.of("n", 1)),
            callEnd(2, 150, "call_001"),
            callStart(3, 300, "call_002", Map.of("n", 2)),
            callEnd(4, 450, "call_002"),
            runCompleted(5, 800)));

    VhsLoadTest loadTest = new VhsLoadTest(meterRegistry, testProps());
    VhsLoadTestReport report = loadTest.run(
            tempDir.resolve("*.vhs.jsonl").toString(), "dry-run", 2.0, 4, 0);

    assertThat(report.target()).isEqualTo("dry-run");
    assertThat(report.tapesLoaded()).isEqualTo(2);
    assertThat(report.totalCalls()).isGreaterThan(0);
    assertThat(report.successfulCalls()).isGreaterThan(0);
    assertThat(report.failedCalls()).isZero();
    assertThat(report.tapeSummaries()).hasSize(2);

    // Verify Micrometer timer was published
    Timer timer = meterRegistry.find("dsl.vhs.replay.duration").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isGreaterThan(0);

    // Verify Micrometer counter was published
    assertThat(meterRegistry.find("dsl.vhs.replay.calls").counter()).isNotNull();
    assertThat(meterRegistry.find("dsl.vhs.replay.calls").counter().count()).isGreaterThan(0);
  }

  @Test
  void loadTestComputesPercentilesPerTape() throws Exception {
    writeTape("single.vhs.jsonl", List.of(
            runStarted(0, 0),
            callStart(1, 0, "call_001", Map.of("n", 1)),
            callEnd(2, 100, "call_001"),
            runCompleted(3, 500)));

    VhsLoadTest loadTest = new VhsLoadTest(meterRegistry, testProps());
    VhsLoadTestReport report = loadTest.run(
            tempDir.resolve("single.vhs.jsonl").toString(), "dry-run", 1.0, 2, 0);

    assertThat(report.tapeSummaries()).hasSize(1);
    VhsLoadTestReport.TapePercentileSummary summary = report.tapeSummaries().get(0);
    assertThat(summary.totalCalls()).isGreaterThan(0);
    assertThat(summary.p50Ms()).isGreaterThanOrEqualTo(0);
    assertThat(summary.p95Ms()).isGreaterThanOrEqualTo(0);
    assertThat(summary.p99Ms()).isGreaterThanOrEqualTo(0);
    assertThat(summary.errorRatePct()).isGreaterThanOrEqualTo(0.0);
    assertThat(summary.errorRatePct()).isLessThanOrEqualTo(100.0);
  }

  @Test
  void loadTestTagsMetricsCorrectly() throws Exception {
    writeTape("tagged.vhs.jsonl", List.of(
            runStarted(0, 0),
            callStart(1, 0, "call_001", Map.of("n", 1)),
            callEnd(2, 50, "call_001"),
            runCompleted(3, 200)));

    VhsLoadTest loadTest = new VhsLoadTest(meterRegistry, testProps());
    loadTest.run(tempDir.resolve("tagged.vhs.jsonl").toString(), "dry-run", 1.0, 1, 0);

    // Timer has correct tags
    Timer timer = meterRegistry.find("dsl.vhs.replay.duration")
            .tag("target", "dry-run")
            .timer();
    assertThat(timer).isNotNull();

    // Counter has status tag
    assertThat(meterRegistry.find("dsl.vhs.replay.calls")
            .tag("status", "success")
            .counter()).isNotNull();
  }

  @Test
  void loadTestRejectsEmptyGlob() {
    VhsLoadTest loadTest = new VhsLoadTest(meterRegistry, testProps());
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> loadTest.run(tempDir.resolve("nonexistent").toString(), "dry-run", 1.0, 1, 0))
            .isInstanceOf(VhsLoadTestException.class)
            .hasMessageContaining("No tape files found");
  }

  @Test
  void percentilesHelperComputesCorrectly() {
    List<Long> values = List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
    long[] p = VhsPercentiles.compute(values, 50, 95, 99);
    // idx = int(10 * 50 / 100) = 5 → values[5] = 60
    assertThat(p[0]).isEqualTo(60L); // p50
    // idx = int(10 * 95 / 100) = 9 → values[9] = 100
    assertThat(p[1]).isEqualTo(100L); // p95
    // idx = int(10 * 99 / 100) = 9 → values[9] = 100
    assertThat(p[2]).isEqualTo(100L); // p99
  }

  @Test
  void percentilesHelperHandlesEmptyList() {
    long[] p = VhsPercentiles.compute(List.of(), 50, 95, 99);
    assertThat(p).containsExactly(0, 0, 0);
  }

  // ---------------------------------------------------------------------------------------------
  // Test doubles
  // ---------------------------------------------------------------------------------------------

  static final class FakeClock implements cbs.nova.starter.vhs.VhsTapeWriter.InstantSource {
    private Instant now;

    FakeClock(Instant start) {
      this.now = start;
    }

    @Override
    public Instant now() {
      return now;
    }
  }
}
