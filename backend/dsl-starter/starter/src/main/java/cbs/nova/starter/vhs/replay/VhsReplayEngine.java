package cbs.nova.starter.vhs.replay;

import cbs.nova.starter.config.properties.CbsVhsReplayProperties;
import cbs.nova.starter.vhs.TapeEvent;
import cbs.nova.starter.vhs.replay.VhsCallDriver.CallResult;
import cbs.nova.starter.vhs.replay.VhsReplayReport.CallObservation;
import cbs.nova.starter.vhs.replay.VhsReplayReport.TapeSummary;
import cbs.nova.starter.vhs.replay.VhsTapeReader.VhsTape;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Replays a validated VHS tape against a {@link VhsCallDriver} target.
 *
 * <p>
 * Two modes ({@link ReplayMode}):
 *
 * <ul>
 * <li><b>exact</b> — one tape, calls in tape order, waiting the original {@code relative_ms} deltas
 * (divided by the speed multiplier, floored at {@code minWaitMs}). Timing deltas and call order are
 * reproduced for deterministic bug reproduction.</li>
 * <li><b>load</b> — N copies of the tape executed concurrently at a speed multiplier, bounded by a
 * concurrency cap and an optional global duration cap. Individual call failures abort only that
 * copy; other copies continue.</li>
 * </ul>
 *
 * <p>
 * The engine never executes a call on a tape that {@link VhsTapeReader} rejects — validation
 * happens up front and throws {@link VhsReplayException} before the first call is dispatched.
 *
 * <p>
 * <b>Operational safety:</b> this engine re-executes real recorded actions. The only sanctioned way
 * to obtain a driver is {@link VhsCallDrivers#resolve}, which enforces the two-key production
 * opt-in. The default target is {@code dry-run} (no side effects).
 */
@Slf4j
public final class VhsReplayEngine {

  /** Source of waits, abstracted so tests can verify timing without real delays. */
  @FunctionalInterface
  public interface Sleeper {

    void sleep(long millis) throws InterruptedException;
  }

  private static final long MS_IN_NANOS = 1_000_000L;

  private final CbsVhsReplayProperties properties;
  private final VhsCallDriver driver;
  private final VhsTapeReader tapeReader;
  private final Sleeper sleeper;

  public VhsReplayEngine(
          @NonNull CbsVhsReplayProperties properties, @NonNull VhsCallDriver driver) {
    this(properties, driver, new VhsTapeReader(), millis -> Thread.sleep(millis));
  }

  public VhsReplayEngine(
          @NonNull CbsVhsReplayProperties properties,
          @NonNull VhsCallDriver driver,
          @NonNull VhsTapeReader tapeReader,
          @NonNull Sleeper sleeper) {
    this.properties = properties;
    this.driver = driver;
    this.tapeReader = tapeReader;
    this.sleeper = sleeper;
  }

  /**
   * Replay one tape file using the configured {@link CbsVhsReplayProperties#mode()}.
   */
  public VhsReplayReport replay(@NonNull Path tapeFile) {
    return switch (properties.mode()) {
      case exact -> replayExact(tapeFile);
      case load -> replayLoad(tapeFile, properties.copies(), properties.speed(),
              properties.concurrency());
    };
  }

  /**
   * Exact replay: single tape, original order and relative timing (divided by {@code speed}).
   *
   * @throws VhsReplayException
   *           before any call executes when the tape is malformed or truncated
   */
  public VhsReplayReport replayExact(@NonNull Path tapeFile) {
    VhsTape tape = tapeReader.read(tapeFile);
    long startNanos = System.nanoTime();
    TapeRun run = runTape(tape, 1.0, tapeId(tapeFile, 0), deadlineNanos(0));
    long wallMs = elapsedMs(startNanos);
    return new VhsReplayReport(
            ReplayMode.exact, 1, run.observations.size(), run.successCount, run.failureCount,
            0, wallMs, run.observations, List.of(run.summary(tapeId(tapeFile, 0), wallMs)));
  }

  /**
   * Load replay: {@code copies} concurrent copies of the tape at the given speed multiplier.
   * Concurrency is bounded by {@code concurrencyCap}; an optional global duration cap
   * ({@code cbs.vhs.replay.max-duration-ms > 0}) stops launching new copies once exceeded — already
   * running copies finish, unstarted copies are reported as skipped.
   *
   * @throws VhsReplayException
   *           before any call executes when the tape is malformed or truncated
   */
  public VhsReplayReport replayLoad(
          @NonNull Path tapeFile, int copies, double speed, int concurrencyCap) {
    if (copies < 1) {
      throw new VhsReplayException("Load replay requires copies >= 1, was " + copies);
    }
    if (speed <= 0) {
      throw new VhsReplayException("Load replay requires speed > 0, was " + speed);
    }
    if (concurrencyCap < 1) {
      throw new VhsReplayException(
              "Load replay requires concurrency cap >= 1, was " + concurrencyCap);
    }
    VhsTape tape = tapeReader.read(tapeFile);
    long startNanos = System.nanoTime();
    long deadlineNanos = deadlineNanos(startNanos);

    ExecutorService pool = Executors.newFixedThreadPool(Math.min(concurrencyCap, copies));
    try {
      List<Future<TapeRun>> futures = new ArrayList<>();
      int skipped = 0;
      for (int i = 0; i < copies; i++) {
        if (deadlineNanos > 0 && System.nanoTime() >= deadlineNanos) {
          skipped = copies - i;
          log.warn("VHS load replay duration cap reached: skipping {} of {} copies",
                  skipped, copies);
          break;
        }
        String tapeId = tapeId(tapeFile, i);
        futures.add(pool.submit(() -> runTape(tape, speed, tapeId, deadlineNanos)));
      }
      List<TapeRun> runs = new ArrayList<>();
      for (Future<TapeRun> future : futures) {
        runs.add(unwrap(future));
      }
      long wallMs = elapsedMs(startNanos);
      long total = 0;
      long success = 0;
      long failed = 0;
      List<CallObservation> observations = new ArrayList<>();
      List<TapeSummary> summaries = new ArrayList<>();
      for (int i = 0; i < runs.size(); i++) {
        TapeRun run = runs.get(i);
        total += run.observations.size();
        success += run.successCount;
        failed += run.failureCount;
        observations.addAll(run.observations);
        summaries.add(run.summary(tapeId(tapeFile, i), run.durationMs));
      }
      return new VhsReplayReport(ReplayMode.load, summaries.size(), total, success, failed,
              skipped, wallMs, observations, summaries);
    } finally {
      pool.shutdownNow();
    }
  }

  private TapeRun runTape(VhsTape tape, double speed, String tapeId, long deadlineNanos) {
    List<CallObservation> observations = new ArrayList<>();
    Map<String, TapeEvent> callEnds = indexCallEnds(tape);
    long baseMs = tape.events().isEmpty() ? 0 : tape.events().get(0).relativeMs();
    long waitedMs = 0;
    long successCount = 0;
    long startNanos = System.nanoTime();
    for (TapeEvent event : tape.events()) {
      String type = event.eventType();
      if (!"call_start".equals(type)) {
        if (!"run_started".equals(type) && !"run_completed".equals(type)
                && !"call_end".equals(type)) {
          log.warn("[VHS replay {}] skipping unknown event type '{}' at index {}",
                  tapeId, type, event.eventIndex());
        }
        continue;
      }
      if (deadlineNanos > 0 && System.nanoTime() >= deadlineNanos) {
        observations.add(errorObservation(tapeId, event,
                "aborted: replay duration cap reached"));
        return new TapeRun(observations, successCount, observations.size() - successCount,
                elapsedMs(startNanos));
      }
      long targetElapsed = Math.max(0L, (long) ((event.relativeMs() - baseMs) / speed));
      long wait = Math.max(targetElapsed - waitedMs, properties.minWaitMs());
      if (wait > 0) {
        try {
          sleeper.sleep(wait);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new VhsReplayException(
                  "VHS replay of tape " + tapeId + " interrupted before call "
                          + callIdOf(event),
                  ex);
        }
        waitedMs += wait;
      }
      long callStartNanos = System.nanoTime();
      CallResult result = driver.execute(event);
      long latencyMs = (System.nanoTime() - callStartNanos) / MS_IN_NANOS;
      TapeEvent end = callEnds.get(callIdOf(event));
      boolean compared = properties.compareOutput() && end != null && end.output() != null;
      boolean mismatch = compared && !outputsEqual(end.output(), result.output());
      observations.add(new CallObservation(
              tapeId,
              callIdOf(event),
              metaOf(event, "type"),
              metaOf(event, "target"),
              metaOf(event, "operation"),
              latencyMs,
              result.success(),
              result.success() ? null : result.error(),
              compared,
              mismatch));
      if (!result.success()) {
        log.warn("[VHS replay {}] call {} failed: {}", tapeId, callIdOf(event), result.error());
        return new TapeRun(observations, successCount,
                observations.size() - successCount, elapsedMs(startNanos));
      }
      successCount++;
    }
    return new TapeRun(observations, successCount, 0, elapsedMs(startNanos));
  }

  private static Map<String, TapeEvent> indexCallEnds(VhsTape tape) {
    Map<String, TapeEvent> ends = new HashMap<>();
    for (TapeEvent event : tape.events()) {
      if ("call_end".equals(event.eventType()) && event.callMetadata() != null) {
        ends.put(event.callMetadata().callId(), event);
      }
    }
    return ends;
  }

  private static CallObservation errorObservation(String tapeId, TapeEvent event, String error) {
    return new CallObservation(tapeId, callIdOf(event), metaOf(event, "type"),
            metaOf(event, "target"), metaOf(event, "operation"), 0, false, error, false, false);
  }

  @Nullable
  private static String callIdOf(TapeEvent event) {
    return event.callMetadata() != null ? event.callMetadata().callId() : null;
  }

  @Nullable
  private static String metaOf(TapeEvent event, String field) {
    TapeEvent.CallMetadata meta = event.callMetadata();
    if (meta == null) {
      return null;
    }
    return switch (field) {
      case "type" -> meta.type();
      case "target" -> meta.target();
      default -> meta.operation();
    };
  }

  private static boolean outputsEqual(@Nullable Object expected, @Nullable Object actual) {
    if (expected == null || actual == null) {
      return expected == actual;
    }
    return expected.equals(actual) || expected.toString().equals(actual.toString());
  }

  private long deadlineNanos(long startNanos) {
    long capMs = properties.maxDurationMs();
    if (capMs <= 0 || startNanos == 0) {
      return capMs > 0 ? System.nanoTime() + capMs * MS_IN_NANOS : 0;
    }
    return startNanos + capMs * MS_IN_NANOS;
  }

  private static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / MS_IN_NANOS;
  }

  private static String tapeId(Path tapeFile, int copy) {
    String name = tapeFile.getFileName().toString();
    return copy == 0 ? name : name + "#" + copy;
  }

  private static TapeRun unwrap(Future<TapeRun> future) {
    try {
      return future.get();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new VhsReplayException("VHS load replay interrupted while collecting results", ex);
    } catch (ExecutionException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof VhsReplayException replayEx) {
        throw replayEx;
      }
      throw new VhsReplayException("VHS load replay copy failed unexpectedly",
              cause != null ? cause : ex);
    }
  }

  private record TapeRun(
          List<CallObservation> observations,
          long successCount,
          long failureCount,
          long durationMs) {

    TapeSummary summary(String tapeId, long wallMs) {
      return new TapeSummary(tapeId, observations.size(), successCount, failureCount, wallMs);
    }
  }
}
