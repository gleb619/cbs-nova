package cbs.nova.starter.service;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.service.DslRunCancellationService.Outcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Slf4j
public class TemporalDslProcessService {

  static final String EMPTY_OUTPUT_JSON = "{}";

  static final Instant NOT_FINISHED_AT = Instant.EPOCH;

  public static final String RUN_DURATION_TIMER = "dsl.run.duration";

  public static final String RUN_COUNT_COUNTER = "dsl.run.count";

  public static final String CANCEL_COUNTER = "dsl.run.cancel";

  public static final String SWEEP_STALE_COUNTER = "dsl.run.sweep.stale";

  public static final String SWEEP_INSPECTED_COUNTER = "dsl.run.sweep.inspected";

  static final String UNKNOWN_PROCESS = "unknown";

  static final String PROCESS_NAME_TAG = "processName";

  static final String STATUS_TAG = "status";

  private final ContextFactory contextFactory;
  private final DslRunRepository runRepository;
  private final ObjectMapper objectMapper;
  private final ThreadPoolTaskExecutor dslProcessExecutor;
  private final ScheduledExecutorService healthcheckExecutor;
  private final Duration healthcheckInterval;
  private final Duration staleThreshold;
  private final boolean asyncDbSave;
  private final long maxOutputBytes;
  private final MeterRegistry meterRegistry;
  private final RunIdentityResolver runIdentityResolver;

  public TemporalDslProcessService(ContextFactory contextFactory, DslRunRepository runRepository,
          ObjectMapper objectMapper,
          ThreadPoolTaskExecutor dslProcessExecutor, ScheduledExecutorService healthcheckExecutor,
          Duration healthcheckInterval, Duration staleThreshold, boolean asyncDbSave,
          MeterRegistry meterRegistry, RunIdentityResolver runIdentityResolver) {
    this(contextFactory, runRepository, objectMapper, dslProcessExecutor, healthcheckExecutor,
            healthcheckInterval, staleThreshold, asyncDbSave, Long.MAX_VALUE, meterRegistry,
            runIdentityResolver);
  }

  public TemporalDslProcessService(ContextFactory contextFactory, DslRunRepository runRepository,
          ObjectMapper objectMapper,
          ThreadPoolTaskExecutor dslProcessExecutor, ScheduledExecutorService healthcheckExecutor,
          Duration healthcheckInterval, Duration staleThreshold, boolean asyncDbSave,
          long maxOutputBytes, MeterRegistry meterRegistry,
          RunIdentityResolver runIdentityResolver) {
    this.contextFactory = contextFactory;
    this.runRepository = runRepository;
    this.objectMapper = objectMapper;
    this.dslProcessExecutor = dslProcessExecutor;
    this.healthcheckExecutor = healthcheckExecutor;
    this.healthcheckInterval = healthcheckInterval;
    this.staleThreshold = staleThreshold;
    this.asyncDbSave = asyncDbSave;
    this.maxOutputBytes = maxOutputBytes;
    this.meterRegistry = meterRegistry;
    this.runIdentityResolver = runIdentityResolver;
  }

  private static final Duration SHUTDOWN_JOIN = Duration.ofSeconds(5);

  private final AtomicReference<Clock> clock = new AtomicReference<>(Clock.systemUTC());

  private final AtomicReference<ScheduledFuture<?>> healthcheckHandle = new AtomicReference<>();

  private final AtomicBoolean healthcheckStarted = new AtomicBoolean(false);

  private OpenTelemetry openTelemetry = OpenTelemetry.noop();

  private final Map<String, Span> activeSpans = new ConcurrentHashMap<>();

  void setClock(@NonNull Clock clock) {
    this.clock.set(clock);
  }

  /**
   * Sets the OpenTelemetry instance used for DSL run tracing. Defaults to a no-op implementation;
   * when left unset, tracing is completely disabled.
   */
  public void setOpenTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry != null ? openTelemetry : OpenTelemetry.noop();
  }

  OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  private @NonNull Instant now() {
    Clock c = clock.get();
    return c != null ? c.instant() : Instant.now();
  }

  public @NonNull ProcessRun runProcess(@NonNull String processName, @Nullable Object input) {
    return startProcess(processName, input, Map.of());
  }

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
      String triggeredBy = runIdentityResolver.resolve();

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
              .triggeredBy(triggeredBy)
              .build();

      submitDbWrite(() -> {
        runRepository.save(running);
        return null;
      });

      ensureHealthcheckStarted();

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

  void ensureHealthcheckForTest() {
    ensureHealthcheckStarted();
  }

  private void ensureHealthcheckStarted() {
    if (!healthcheckStarted.compareAndSet(false, true)) {
      return;
    }
    long intervalMs = Math.max(1L, healthcheckInterval.toMillis());
    try {
      ScheduledFuture<?> fresh = healthcheckExecutor.scheduleWithFixedDelay(
              this::healthcheckSweep, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
      ScheduledFuture<?> previous = healthcheckHandle.getAndSet(fresh);
      if (previous != null) {
        previous.cancel(false);
      }
    } catch (Exception ex) {
      healthcheckStarted.set(false);
      healthcheckHandle.set(null);
    }
  }

  public void shutdownHealthcheck() {
    healthcheckStarted.set(false);
    ScheduledFuture<?> current = healthcheckHandle.getAndSet(null);
    if (current == null) {
      return;
    }
    current.cancel(false);
    try {
      current.get(SHUTDOWN_JOIN.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException expected) {
    } catch (CancellationException expected) {
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException ignored) {
    }
  }

  private void healthcheckSweep() {
    Instant cutoff = now().minus(staleThreshold);
    try {
      for (String processName : knownProcessNames()) {
        for (DslRun run : runRepository.findByProcessName(processName)) {
          meterRegistry.counter(SWEEP_INSPECTED_COUNTER,
                  PROCESS_NAME_TAG, safeProcessName(run.processName())).increment();
          if (!DslRunStatus.RUNNING.name().equals(run.status())) {
            continue;
          }
          Instant startedAt = run.startedAt();
          if (startedAt.isAfter(cutoff)) {
            continue;
          }
          markStale(run);
        }
      }
    } catch (Exception ex) {
      log.warn("DSL process healthcheck sweep failed: {}", ex.getMessage(), ex);
    }
  }

  private void markStale(@NonNull DslRun run) {
    String runId = run.runId();
    var scope = propagateRunId(runId);
    try {
      Instant finishedAt = now();
      submitDbWrite(() -> {
        int affected = runRepository.updateFinishedIfRunning(
                runId,
                DslRunStatus.STALE.name(),
                EMPTY_OUTPUT_JSON,
                "Run exceeded staleness threshold " + staleThreshold
                        + " without producing a final status",
                finishedAt,
                null);
        if (affected == 0) {
          log.info("Sweep skipped staleness mark for run {}: it is no longer RUNNING "
                  + "(concurrent terminal transition)", runId);
        }
        return null;
      });

      meterRegistry.counter(SWEEP_STALE_COUNTER,
              PROCESS_NAME_TAG, safeProcessName(run.processName())).increment();
      recordRunComplete(run.processName(), DslRunStatus.STALE.name(), run.startedAt(), finishedAt);

      Span span = activeSpans.remove(runId);
      if (span != null) {
        span.setAttribute("status", DslRunStatus.STALE.name());
        span.setStatus(StatusCode.ERROR, "Run marked stale by healthcheck");
        span.end();
      }
    } finally {
      try {
        scope.close();
      } catch (Exception ignored) {
      }
    }
  }

  private @NonNull Set<String> knownProcessNames() {
    Set<String> names = new HashSet<>(runRepository.knownProcessNames());
    names.addAll(GlobalManager.globalManager().processNames());
    return names;
  }

  private @NonNull String safeProcessName(@Nullable String processName) {
    if (processName == null || processName.isBlank()) {
      return UNKNOWN_PROCESS;
    }
    return GlobalManager.globalManager().processNames().contains(processName)
            ? processName
            : UNKNOWN_PROCESS;
  }

  private void recordRunComplete(@Nullable String processName, @NonNull String status,
          @NonNull Instant startedAt, @NonNull Instant finishedAt) {
    String safe = safeProcessName(processName);
    Duration duration = Duration.between(startedAt, finishedAt);
    if (duration.isNegative()) {
      duration = Duration.ZERO;
    }
    Timer.builder(RUN_DURATION_TIMER)
            .description("Duration of a production DSL run")
            .tag(PROCESS_NAME_TAG, safe)
            .tag(STATUS_TAG, status)
            .register(meterRegistry)
            .record(duration);
    meterRegistry.counter(RUN_COUNT_COUNTER,
            PROCESS_NAME_TAG, safe,
            STATUS_TAG, status).increment();
  }

  public void recordCancel(@Nullable String processName, @Nullable Instant startedAt,
          @NonNull Outcome outcome, @NonNull Instant finishedAt) {
    String safe = safeProcessName(processName);
    String status = switch (outcome) {
      case CANCELLED -> "cancelled";
      case NOT_FOUND -> "notfound";
      case NOT_CANCELLABLE -> "rejected";
    };
    meterRegistry.counter(CANCEL_COUNTER,
            STATUS_TAG, status,
            PROCESS_NAME_TAG, safe).increment();
    if (outcome == Outcome.CANCELLED && startedAt != null) {
      recordRunComplete(processName, DslRunStatus.CANCELLED.name(), startedAt, finishedAt);
    }
  }

  private @NonNull Result<?> executeAndRecord(
          @NonNull String processName,
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull String runId,
          @NonNull Instant startedAt) {
    return doExecuteAndRecord(processName, body, metadata, runId, startedAt);
  }

  private @NonNull Result<?> doExecuteAndRecord(
          @NonNull String processName,
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull String runId,
          @NonNull Instant startedAt) {
    Tracer tracer = openTelemetry.getTracer("cbs.nova.dsl");
    Span span = tracer.spanBuilder("dsl.run." + processName)
            .setAttribute("runId", runId)
            .setAttribute("processName", processName)
            .setAttribute("executionMode", ExecutionMode.RUN.name())
            .startSpan();
    activeSpans.put(runId, span);
    try (Scope ignored = span.makeCurrent()) {
      ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();
      Context<?> ctx = contextFactory.of(body, metadata, ExecutionMode.RUN, runId)
              .withExecutionTraceCollector(traceCollector);
      traceCollector.start();
      Result<?> result;
      try {
        result = GlobalManager.globalManager().runProcess(processName, ctx);
      } catch (Exception ex) {
        result = Result.failure(ex);
      }
      // T296: snapshot BEFORE stop() — ExecutionTraceCollector.stop() clears its
      // entries, so reading after stop() always yielded an empty list and
      // context_json was persisted as null. Keep stop() afterwards to free the
      // queue as soon as the snapshot is captured.
      List<String> traceSnapshot = List.copyOf(traceCollector.snapshot());
      traceCollector.stop();

      Instant finishedAt = now();
      String contextJson = serializeTrace(traceSnapshot);
      String status = result.isSuccess()
              ? DslRunStatus.COMPLETED.name()
              : DslRunStatus.FAILED.name();
      String outputJson = result.isSuccess() ? serialize(result.value()) : EMPTY_OUTPUT_JSON;
      String error = result.isSuccess() ? null : messageOf(result.cause());

      if (result.isSuccess()) {
        OutputTruncation truncation = truncateIfNeeded(outputJson);
        if (truncation != null) {
          outputJson = truncation.outputJson();
          error = truncation.errorMessage();
        }
      }

      String finalOutputJson = outputJson;
      String finalError = error;

      span.setAttribute("status", status);
      if (!result.isSuccess()) {
        span.setStatus(StatusCode.ERROR, finalError);
      }

      submitDbWrite(() -> {
        runRepository.updateFinished(
                runId,
                status,
                finalOutputJson,
                finalError,
                finishedAt,
                contextJson);
        return null;
      });

      recordRunComplete(processName, status, startedAt, finishedAt);

      return result;
    } finally {
      endSpan(runId);
    }
  }

  private void endSpan(@NonNull String runId) {
    Span span = activeSpans.remove(runId);
    if (span != null) {
      span.end();
    }
  }

  private void submitDbWrite(@NonNull Supplier<Void> write) {
    if (!asyncDbSave) {
      write.get();
      return;
    }
    dslProcessExecutor.execute(() -> {
      try {
        write.get();
      } catch (RuntimeException ex) {
        log.warn("Async DSL DB write failed: {}", ex.getMessage(), ex);
      }
    });
  }

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

  private record OutputTruncation(String outputJson, String errorMessage) {
  }

  private @Nullable OutputTruncation truncateIfNeeded(@Nullable String outputJson) {
    long limit = effectiveOutputLimit();
    if (outputJson == null || limit == Long.MAX_VALUE) {
      return null;
    }
    long originalBytes = outputJson.getBytes(StandardCharsets.UTF_8).length;
    if (originalBytes <= limit) {
      return null;
    }
    String truncated = "{\"truncated\":true,\"originalBytes\":" + originalBytes + "}";
    String message = "Output was truncated: original size " + originalBytes
            + " bytes exceeded max output size " + limit + " bytes";
    return new OutputTruncation(truncated, message);
  }

  private long effectiveOutputLimit() {
    return maxOutputBytes <= 0 ? Long.MAX_VALUE : maxOutputBytes;
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

  public record ProcessRun(@NonNull String runId,
          @NonNull CompletableFuture<Result<?>> result) {
  }

}
