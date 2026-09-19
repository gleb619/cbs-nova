package cbs.nova.starter.vhs.loadtest;

import cbs.nova.starter.config.properties.CbsVhsReplayProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.vhs.replay.ReplayMode;
import cbs.nova.starter.vhs.replay.VhsReplayEngine;
import cbs.nova.starter.vhs.replay.VhsReplayReport;
import cbs.nova.starter.vhs.replay.VhsTapeReader;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Thin orchestration layer on top of {@link VhsReplayEngine}: runs a set of tapes at a
 * target/speed/concurrency, aggregates per-tape {@link VhsReplayReport} instances, and publishes
 * Micrometer meters via the existing starter {@link MeterRegistry}.
 */
@Slf4j
public final class VhsLoadTest {

  private final MeterRegistry meterRegistry;
  private final CbsVhsReplayProperties replayProperties;
  private final VhsTapeReader tapeReader;

  public VhsLoadTest(
          @NonNull MeterRegistry meterRegistry,
          @NonNull CbsVhsReplayProperties replayProperties) {
    this(meterRegistry, replayProperties, new VhsTapeReader());
  }

  public VhsLoadTest(
          @NonNull MeterRegistry meterRegistry,
          @NonNull CbsVhsReplayProperties replayProperties,
          @NonNull VhsTapeReader tapeReader) {
    this.meterRegistry = meterRegistry;
    this.replayProperties = replayProperties;
    this.tapeReader = tapeReader;
  }

  /**
   * Run a load test against the given tapes glob.
   *
   * @param tapesGlob
   *          path pattern or directory containing {@code *.vhs.jsonl} files
   * @param target
   *          replay target (e.g. "dry-run", "local")
   * @param speed
   *          replay speed multiplier
   * @param concurrency
   *          concurrency cap for load replay
   * @param durationMs
   *          optional global duration cap (0 = no cap, run all copies)
   * @return aggregated report with per-tape percentiles
   */
  public VhsLoadTestReport run(
          @NonNull String tapesGlob,
          @NonNull String target,
          double speed,
          int concurrency,
          long durationMs) {

    log.warn(
            "=== VHS LOAD TEST STARTING === target={} speed={} concurrency={} durationMs={} glob={}",
            target, speed, concurrency, durationMs, tapesGlob);

    List<Path> tapeFiles = resolveTapeFiles(tapesGlob);
    if (tapeFiles.isEmpty()) {
      throw new VhsLoadTestException("No tape files found matching: " + tapesGlob);
    }

    log.warn("=== VHS LOAD TEST: {} tape(s) resolved ===", tapeFiles.size());

    CbsVhsReplayProperties testProps = buildTestProps(target, speed, concurrency, durationMs);
    cbs.nova.starter.vhs.replay.VhsCallDriver driver = cbs.nova.starter.vhs.replay.VhsCallDrivers
            .resolve(testProps);
    VhsReplayEngine engine = new VhsReplayEngine(testProps, driver);

    long startNanos = System.nanoTime();
    List<VhsReplayReport> reports = new ArrayList<>();

    for (Path tapeFile : tapeFiles) {
      try {
        VhsReplayReport report = engine.replayLoad(tapeFile, testProps.copies(), testProps.speed(),
                testProps.concurrency());
        reports.add(report);
        publishMetrics(report, target);
      } catch (cbs.nova.starter.vhs.replay.VhsReplayException ex) {
        log.warn("[VHS load-test] failed to replay {}: {}", tapeFile.getFileName(),
                ex.getMessage());
      }
    }

    long wallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

    return buildReport(reports, target, tapeFiles.size(), wallMs);
  }

  private void publishMetrics(VhsReplayReport report, String target) {
    for (VhsReplayReport.CallObservation obs : report.observations()) {
      String tapeTag = obs.tapeId() != null ? obs.tapeId() : "unknown";
      String processTag = obs.target() != null ? obs.target() : "unknown";

      Timer.builder(StarterConstants.VHS_REPLAY_DURATION_TIMER)
              .tag(StarterConstants.VHS_TARGET_TAG, target)
              .tag(StarterConstants.VHS_TAPE_TAG, tapeTag)
              .tag(StarterConstants.VHS_PROCESS_TAG, processTag)
              .publishPercentiles(0.5, 0.95, 0.99)
              .register(meterRegistry)
              .record(obs.latencyMs(), TimeUnit.MILLISECONDS);

      Counter.builder(StarterConstants.VHS_REPLAY_CALLS_COUNTER)
              .tag(StarterConstants.VHS_TARGET_TAG, target)
              .tag(StarterConstants.VHS_TAPE_TAG, tapeTag)
              .tag(StarterConstants.VHS_PROCESS_TAG, processTag)
              .tag(StarterConstants.STATUS_TAG, obs.success() ? "success" : "error")
              .register(meterRegistry)
              .increment();
    }
  }

  private static VhsLoadTestReport buildReport(
          List<VhsReplayReport> reports, String target, int tapesLoaded, long wallMs) {
    long totalCalls = 0;
    long successfulCalls = 0;
    long failedCalls = 0;
    List<VhsLoadTestReport.TapePercentileSummary> summaries = new ArrayList<>();

    for (VhsReplayReport report : reports) {
      totalCalls += report.totalCalls();
      successfulCalls += report.successfulCalls();
      failedCalls += report.failedCalls();

      List<Long> latencies = report.observations().stream()
              .map(VhsReplayReport.CallObservation::latencyMs)
              .sorted()
              .toList();
      long[] percentiles = VhsPercentiles.compute(latencies, 50, 95, 99);
      double errorRate = report.totalCalls() > 0
              ? (double) report.failedCalls() / report.totalCalls() * 100.0
              : 0.0;

      String tapeName = report.tapes().isEmpty()
              ? "unknown"
              : report.tapes().get(0).tapeId();
      summaries.add(new VhsLoadTestReport.TapePercentileSummary(
              tapeName, report.totalCalls(), report.successfulCalls(), report.failedCalls(),
              percentiles[0], percentiles[1], percentiles[2], errorRate));
    }

    return new VhsLoadTestReport(target, tapesLoaded, totalCalls, successfulCalls, failedCalls,
            wallMs, summaries, reports);
  }

  private static CbsVhsReplayProperties buildTestProps(
          String target, double speed, int concurrency, long durationMs) {
    return new CbsVhsReplayProperties(
            true, target, false, ReplayMode.load, 1, speed, concurrency, durationMs, false, 0,
            30000, "http://localhost:8080",
            CbsVhsReplayProperties.Faking.disabled());
  }

  private static List<Path> resolveTapeFiles(String glob) {
    List<Path> result = new ArrayList<>();
    Path globPath = Path.of(glob);
    if (Files.isDirectory(globPath)) {
      try (DirectoryStream<Path> ds = Files.newDirectoryStream(globPath, "*.vhs.jsonl")) {
        ds.forEach(result::add);
      } catch (IOException ex) {
        throw new VhsLoadTestException("Failed to list tapes in " + glob, ex);
      }
      result.sort(Comparator.comparing(p -> p.getFileName().toString()));
      return result;
    }
    Path parent = globPath.getParent();
    String pattern = globPath.getFileName() != null
            ? globPath.getFileName().toString()
            : "*.vhs.jsonl";
    if (parent == null || !Files.isDirectory(parent)) {
      throw new VhsLoadTestException("Invalid tape glob path: " + glob);
    }
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(parent, pattern)) {
      ds.forEach(result::add);
    } catch (IOException ex) {
      throw new VhsLoadTestException("Failed to resolve tape glob: " + glob, ex);
    }
    result.sort(Comparator.comparing(p -> p.getFileName().toString()));
    return result;
  }
}
