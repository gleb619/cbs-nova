package cbs.nova.starter.services;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslRun;
import cbs.nova.dsl.DslRunRepository;
import cbs.nova.dsl.DslRunStatus;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
public class TemporalDslProcessService {

  /** Typed-empty JSON placeholder for {@link DslRun#output()} while a run is still in flight. */
  static final String EMPTY_OUTPUT_JSON = "{}";

  /**
   * Typed-empty sentinel for {@link DslRun#finishedAt()} while the run has not produced a final
   * status. Using a sentinel {@code Instant} (instead of {@code null}) keeps the column strictly
   * non-null at the storage layer and survives JSON serialization round-trips.
   */
  static final Instant NOT_FINISHED_AT = Instant.EPOCH;

  private final ContextFactory contextFactory;
  private final DslRunRepository runRepository;
  private final ObjectMapper objectMapper;
  private final ThreadPoolTaskExecutor dslProcessExecutor;
  private final ScheduledExecutorService healthcheckExecutor;
  private final Duration healthcheckInterval;
  private final Duration staleThreshold;
  private final boolean asyncDbSave;

  @Nullable
  private volatile Clock clock = Clock.systemUTC();

  @Nullable
  private volatile ScheduledFuture<?> healthcheckHandle;

  private final AtomicBoolean healthcheckStarted = new AtomicBoolean(false);

  /**
   * @param asyncDbSave
   *          When {@code true} (default), repository writes are dispatched to the
   *          {@code dslProcessExecutor}; when {@code false}, writes run on the calling thread for
   *          tests that prefer fully synchronous behaviour.
   */
  public TemporalDslProcessService(
          @NonNull ContextFactory contextFactory,
          @NonNull DslRunRepository runRepository,
          @NonNull ObjectMapper objectMapper,
          @NonNull ThreadPoolTaskExecutor dslProcessExecutor,
          @NonNull ScheduledExecutorService healthcheckExecutor,
          @NonNull Duration healthcheckInterval,
          @NonNull Duration staleThreshold,
          boolean asyncDbSave) {
    this.contextFactory = contextFactory;
    this.runRepository = runRepository;
    this.objectMapper = objectMapper;
    this.dslProcessExecutor = dslProcessExecutor;
    this.healthcheckExecutor = healthcheckExecutor;
    this.healthcheckInterval = healthcheckInterval;
    this.staleThreshold = staleThreshold;
    this.asyncDbSave = asyncDbSave;
  }

  /**
   * Legacy constructor retained for callers that want to wire the service by hand (test setups,
   * standalone scripts). Internally wires a synchronous executor and a disabled healthcheck so
   * existing fully-blocking semantics are preserved. Production wiring should use the public 8-arg
   * constructor through {@link cbs.nova.starter.config.TemporalConfiguration}.
   */
  public TemporalDslProcessService(
          ContextFactory contextFactory,
          DslRunRepository runRepository,
          ObjectMapper objectMapper) {
    this(contextFactory, runRepository, objectMapper, sameThreadExecutor(),
            disabledScheduledExecutor(),
            Duration.ofSeconds(30), Duration.ofMinutes(5), false);
  }

  /** Overrides the clock used for {@code startedAt} / staleness checks (test seam). */
  void setClock(@NonNull Clock clock) {
    this.clock = clock;
  }

  private @NonNull Instant now() {
    Clock c = clock;
    return c != null ? c.instant() : Instant.now();
  }

  /**
   * Non-blocking entry point: returns a {@link ProcessRun} handle immediately. The returned future
   * completes with the actual outcome once the workflow finishes and the run is recorded as
   * {@code COMPLETED} or {@code FAILED}. See {@link #startProcess(String, Object)} for full
   * semantics.
   */
  public @NonNull ProcessRun runProcess(@NonNull String processName, @Nullable Object input) {
    return startProcess(processName, input, Map.of());
  }

  /**
   * Starts a process asynchronously, returning as soon as the {@link DslRun} is recorded as
   * {@code RUNNING} and the workflow has been launched. The returned handle exposes the generated
   * {@code runId} (useful for correlating side effects such as latch files) and a future that
   * completes with the outcome once the run is recorded as {@code COMPLETED} or {@code FAILED}.
   *
   * <p>
   * This is the non-blocking counterpart of the historical blocking API and is required for
   * scenarios that need to mutate global state (e.g. reload DSL definitions) while a workflow is
   * still in flight.
   */
  public @NonNull ProcessRun startProcess(@NonNull String processName, @Nullable Object input) {
    return startProcess(processName, input, Map.of());
  }

  public @NonNull ProcessRun startProcess(
          @NonNull String processName,
          @Nullable Object input,
          @NonNull Map<String, Object> metadata) {
    Object body = input != null ? input : Map.of();
    String runId = contextFactory.generateRunId();
    var runIdScope = propagateRunId(runId);
    try {
      String inputJson = serialize(body);
      Instant startedAt = now();

      DslRun running = DslRun.builder()
              .runId(runId)
              .processName(processName)
              .status(DslRunStatus.RUNNING.name())
              .input(inputJson)
              .output(EMPTY_OUTPUT_JSON)
              .error(null)
              .startedAt(startedAt)
              .finishedAt(NOT_FINISHED_AT)
              .executionMode(ExecutionMode.RUN.name())
              .build();

      submitDbWrite(() -> {
        runRepository.save(running);
        return null;
      });

      // Lazily start the healthcheck once we have at least one tracked run. Idempotent.
      ensureHealthcheckStarted();

      // Pass the ThreadPoolTaskExecutor as the Executor interface — its TaskDecorator will
      // replay MDC on the worker thread, and submit()/supplyAsync payloads are dispatched via
      // execute() which honours our same-thread override for synchronous test wiring.
      CompletableFuture<Result<?>> result = CompletableFuture.supplyAsync(
              () -> executeAndRecord(processName, body, metadata, runId, startedAt),
              dslProcessExecutor);
      return new ProcessRun(runId, result);
    } finally {
      try {
        runIdScope.close();
      } catch (Exception ignored) {
      }
    }
  }

  /**
   * Lazily schedules the background healthcheck that scans for {@code RUNNING} runs older than
   * {@link #staleThreshold} and flips their status to {@link DslRunStatus#STALE}. Guarded by an
   * {@link AtomicBoolean} so concurrent starts from multiple {@code startProcess} calls on the same
   * JVM do not pile up overlapping schedules.
   */
  void ensureHealthcheckForTest() {
    ensureHealthcheckStarted();
  }

  private void ensureHealthcheckStarted() {
    if (!healthcheckStarted.compareAndSet(false, true)) {
      return;
    }
    long intervalMs = Math.max(1L, healthcheckInterval.toMillis());
    try {
      healthcheckHandle = healthcheckExecutor.scheduleWithFixedDelay(
              this::healthcheckSweep, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    } catch (Exception ex) {
      // Healthcheck executor already shut down (e.g. tests). Reset the latch so a future call on a
      // live executor still has a chance to start.
      healthcheckStarted.set(false);
    }
  }

  /**
   * Single sweep: enumerate every persisted run, and for each one that is still {@code RUNNING}
   * with {@code startedAt} older than the threshold, flip it to {@code STALE}. The
   * {@link DslRunRepository} only exposes {@code findByProcessName}, so we enumerate the known
   * process names through {@link GlobalManager} — this keeps the staleness detector decoupled from
   * a dedicated "list all" repository method.
   */
  private void healthcheckSweep() {
    Instant cutoff = now().minus(staleThreshold);
    try {
      for (String processName : knownProcessNames()) {
        for (DslRun run : runRepository.findByProcessName(processName)) {
          if (!DslRunStatus.RUNNING.name().equals(run.status())) {
            continue;
          }
          Instant startedAt = run.startedAt();
          if (startedAt == null || startedAt.isAfter(cutoff)) {
            continue;
          }
          markStale(run);
        }
      }
    } catch (Exception ex) {
      // Healthcheck must never poison the executor — log and keep going.
      log.warn("DSL process healthcheck sweep failed: {}", ex.getMessage(), ex);
    }
  }

  private void markStale(@NonNull DslRun run) {
    String runId = run.runId();
    var scope = propagateRunId(runId);
    try {
      Instant finishedAt = now();
      submitDbWrite(() -> {
        runRepository.updateFinished(
                runId,
                DslRunStatus.STALE.name(),
                /* output */ EMPTY_OUTPUT_JSON,
                /* error */ "Run exceeded staleness threshold " + staleThreshold
                        + " without producing a final status",
                finishedAt,
                /* contextJson */ null);
        return null;
      });
    } finally {
      try {
        scope.close();
      } catch (Exception ignored) {
      }
    }
  }

  private @NonNull Set<String> knownProcessNames() {
    Set<String> names = new HashSet<>(runRepository.knownProcessNames());
    // Include all currently-registered processes too — they may have started runs that have
    // since been finalised, but we still want to be robust against run-time registration churn.
    for (String name : GlobalManager.globalManager().processNames()) {
      names.add(name);
    }
    return names;
  }

  private @NonNull Result<?> executeAndRecord(
          @NonNull String processName,
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull String runId,
          @NonNull Instant startedAt) {
    // In production, the Spring TaskDecorator attached to the executor replays MDC/Sentry/OTel
    // context on the worker thread. The caller (startProcess) already opened the scope, so the
    // decorator carries it across the async boundary — we do NOT re-propagate here, otherwise
    // Sentry.setTag would fire twice (once on the launching thread, once on the worker).
    return doExecuteAndRecord(processName, body, metadata, runId, startedAt);
  }

  private @NonNull Result<?> doExecuteAndRecord(
          @NonNull String processName,
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull String runId,
          @NonNull Instant startedAt) {
    ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();
    Context<?> ctx = contextFactory.of(body, metadata, ExecutionMode.RUN, runId)
            .withExecutionTraceCollector(traceCollector);
    traceCollector.start();
    Result<?> result;
    try {
      result = GlobalManager.globalManager().runProcess(processName, ctx);
    } catch (Exception ex) {
      result = Result.failure(ex);
    } finally {
      traceCollector.stop();
    }

    Instant finishedAt = now();
    String contextJson = serializeTrace(traceCollector.snapshot());
    String status = result.isSuccess()
            ? DslRunStatus.COMPLETED.name()
            : DslRunStatus.FAILED.name();
    String outputJson = result.isSuccess() ? serialize(result.value()) : EMPTY_OUTPUT_JSON;
    String error = result.isSuccess() ? null : messageOf(result.cause());

    Instant finalizeFinishedAt = finishedAt;
    submitDbWrite(() -> {
      runRepository.updateFinished(
              runId,
              status,
              outputJson,
              error,
              finalizeFinishedAt,
              contextJson);
      return null;
    });

    return result;
  }

  private void submitDbWrite(@NonNull Supplier<Void> write) {
    if (!asyncDbSave) {
      write.get();
      return;
    }
    // The Spring TaskDecorator attached to the executor restores MDC on the worker thread, so
    // the runId correlation key follows the task. We still guard the call so a failed write
    // cannot poison the executor.
    dslProcessExecutor.execute(() -> {
      try {
        write.get();
      } catch (RuntimeException ex) {
        log.warn("Async DSL DB write failed: {}", ex.getMessage(), ex);
      }
    });
  }

  /**
   * Sets MDC, Sentry tag and OTel baggage for the current thread, returning an
   * {@link AutoCloseable} that undoes all three. Mirrors the {@code propagateRunId} helper that
   * existed prior to the async executor wiring.
   */
  private AutoCloseable propagateRunId(@NonNull String runId) {
    MDC.put("runId", runId);
    try {
      Sentry.setTag("runId", runId);
    } catch (Exception ignored) {
      // Sentry is optional; unconfigured SDK calls are no-ops, but guard defensively.
    }

    AutoCloseable[] otelScope = new AutoCloseable[]{() -> {
    }};
    try {
      otelScope[0] = Baggage.current().toBuilder().put("runId", runId).build().makeCurrent();
      var span = Span.current();
      if (span != null) {
        span.setAttribute("runId", runId);
      }
    } catch (Exception ignored) {
      // OTel api is a hard dep, but guard against any runtime issues.
    }
    return () -> {
      MDC.remove("runId");
      try {
        otelScope[0].close();
      } catch (Exception ignored) {
      }
    };
  }

  private @NonNull String serialize(@Nullable Object value) {
    if (value == null) {
      return "null";
    }

    return objectMapper.writeValueAsString(value);
  }

  private @Nullable String serializeTrace(@NonNull List<String> trace) {
    if (trace.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(Map.of("trace", trace));
    } catch (Exception e) {
      return null;
    }
  }

  private @Nullable String messageOf(@Nullable Throwable cause) {
    return cause != null
            ? (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName())
            : "unknown";
  }

  /**
   * Handle for an asynchronously started process. {@link #runId()} is the generated identifier used
   * for both the persisted {@link DslRun} and the Temporal workflow id; {@link #result()} completes
   * with the outcome once the run is finalized.
   */
  public record ProcessRun(@NonNull String runId,
          @NonNull CompletableFuture<Result<?>> result) {
  }

  private static @NonNull ThreadPoolTaskExecutor sameThreadExecutor() {
    // Test/legacy helper: a synchronous facade over a real (single-worker) pool, so callers
    // that reach for getThreadPoolExecutor() / CompletableFuture.supplyAsync still see a real
    // ExecutorService handle while every task runs inline on the calling thread. The single
    // worker + SynchronousQueue ensures submitted runnables execute serially on the caller.
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor() {
      @Override
      public void execute(@NonNull Runnable command) {
        command.run();
      }
    };
    exec.setCorePoolSize(1);
    exec.setMaxPoolSize(1);
    exec.setQueueCapacity(0);
    exec.setThreadNamePrefix("cbs-nova-dsl-sync-");
    exec.initialize();
    return exec;
  }

  private static @NonNull ScheduledExecutorService disabledScheduledExecutor() {
    ThreadFactory tf = r -> {
      Thread t = new Thread(r, "cbs-nova-dsl-healthcheck-disabled");
      t.setDaemon(true);
      return t;
    };
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(tf);
    exec.shutdownNow();
    return exec;
  }
}
