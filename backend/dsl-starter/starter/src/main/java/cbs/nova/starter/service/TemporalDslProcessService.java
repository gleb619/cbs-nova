package cbs.nova.starter.service;

import static cbs.nova.starter.core.StarterConstants.SERVICE_SHUTDOWN_JOIN;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.listener.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.sse.ExecutionStatusEventPublisher;
import cbs.nova.starter.service.DslRunCancellationService.Outcome;
import cbs.nova.starter.webhook.WebhookDispatcher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.sentry.Sentry;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Slf4j
public class TemporalDslProcessService {

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
  private final Optional<WebhookDispatcher> webhookDispatcher;
  private final OpenTelemetry openTelemetry;
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;
  private final ObjectProvider<TransactionTemplate> transactionTemplateProvider;
  private @Nullable ExecutionStatusEventPublisher statusPublisher;

  public TemporalDslProcessService(
          ContextFactory contextFactory,
          DslRunRepository runRepository,
          ObjectMapper objectMapper,
          ThreadPoolTaskExecutor dslProcessExecutor,
          ScheduledExecutorService healthcheckExecutor,
          Duration healthcheckInterval,
          Duration staleThreshold,
          boolean asyncDbSave,
          long maxOutputBytes,
          MeterRegistry meterRegistry,
          RunIdentityResolver runIdentityResolver,
          Optional<WebhookDispatcher> webhookDispatcher,
          OpenTelemetry openTelemetry,
          ObjectProvider<DomainEventPublisher> eventPublisherProvider,
          ObjectProvider<TransactionTemplate> transactionTemplateProvider) {
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
    this.webhookDispatcher = webhookDispatcher;
    this.openTelemetry = openTelemetry;
    this.eventPublisherProvider = eventPublisherProvider;
    this.transactionTemplateProvider = transactionTemplateProvider;
  }

  public static TemporalDslProcessService withDefaults(
          ContextFactory contextFactory, DslRunRepository runRepository, ObjectMapper objectMapper,
          ThreadPoolTaskExecutor dslProcessExecutor, ScheduledExecutorService healthcheckExecutor,
          Duration healthcheckInterval, Duration staleThreshold, boolean asyncDbSave,
          long maxOutputBytes, MeterRegistry meterRegistry,
          RunIdentityResolver runIdentityResolver) {
    return new TemporalDslProcessService(contextFactory, runRepository, objectMapper,
            dslProcessExecutor, healthcheckExecutor, healthcheckInterval, staleThreshold,
            asyncDbSave, maxOutputBytes, meterRegistry, runIdentityResolver,
            Optional.empty(), OpenTelemetry.noop(),
            EmptyObjectProvider.of(DomainEventPublisher.class),
            EmptyObjectProvider.of(TransactionTemplate.class));
  }

  private final AtomicReference<Clock> clock = new AtomicReference<>(Clock.systemUTC());

  private final AtomicReference<ScheduledFuture<?>> healthcheckHandle = new AtomicReference<>();

  private final AtomicBoolean healthcheckStarted = new AtomicBoolean(false);

  private final Map<String, Span> activeSpans = new ConcurrentHashMap<>();

  void setClock(@NonNull Clock clock) {
    this.clock.set(clock);
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

  public @NonNull ProcessRun runProcess(
          @NonNull String processName,
          @Nullable Object input,
          @Nullable String correlationId) {
    Map<String, Object> metadata = correlationId != null && !correlationId.isBlank()
            ? Map.of(StarterConstants.CORRELATION_ID_METADATA_KEY, correlationId)
            : Map.of();
    return startProcess(processName, input, metadata);
  }

  public @NonNull ProcessRun startProcess(@NonNull String processName, @Nullable Object input) {
    return startProcess(processName, input, Map.of());
  }

  public @NonNull ProcessRun startProcess(
          @NonNull String processName,
          @Nullable Object input,
          @NonNull Map<String, Object> metadata) {
    return startProcess(processName, input, metadata,
            CorrelationId.fromMetadata(metadata.get(StarterConstants.CORRELATION_ID_METADATA_KEY)));
  }

  public @NonNull ProcessRun startProcess(
          @NonNull String processName,
          @Nullable Object input,
          @NonNull Map<String, Object> metadata,
          @Nullable String correlationId) {
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
              .output(StarterConstants.EMPTY_OUTPUT_JSON)
              .error(null)
              .startedAt(startedAt)
              .finishedAt(StarterConstants.NOT_FINISHED_AT)
              .executionMode(ExecutionMode.RUN.name())
              .triggeredBy(triggeredBy)
              .correlationId(correlationId)
              // T492: descriptor-identity hash of the definition this run executes; null
              // (never an exception) when the definition cannot be resolved.
              .definitionHash(RunDefinitionHash.of(processName))
              .build();

      submitDbWrite(() -> {
        runRepository.save(running);
        publishEvent(new DomainEvent.RunStarted(
                runId, processName, triggeredBy, startedAt, correlationId));
        publishStatusChanged(runId, DslRunStatus.RUNNING.name());
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

  @Autowired(required = false)
  public void setExecutionStatusEventPublisher(@Nullable ExecutionStatusEventPublisher publisher) {
    this.statusPublisher = publisher;
  }

  private void publishStatusChanged(@NonNull String runId, @NonNull String status) {
    if (statusPublisher == null) {
      return;
    }
    statusPublisher.publish(runId, status);
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
      current.get(SERVICE_SHUTDOWN_JOIN.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException | CancellationException | ExecutionException _) {
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private void healthcheckSweep() {
    Instant cutoff = now().minus(staleThreshold);
    try {
      for (String processName : knownProcessNames()) {
        for (DslRun run : runRepository.findByProcessName(processName)) {
          meterRegistry.counter(StarterConstants.SWEEP_INSPECTED_COUNTER,
                  StarterConstants.PROCESS_NAME_TAG, safeProcessName(run.processName()))
                  .increment();
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
                StarterConstants.EMPTY_OUTPUT_JSON,
                "Run exceeded staleness threshold " + staleThreshold
                        + " without producing a final status",
                finishedAt,
                null);
        if (affected == 0) {
          log.info("Sweep skipped staleness mark for run {}: it is no longer RUNNING "
                  + "(concurrent terminal transition)", runId);
        } else {
          publishEvent(new DomainEvent.RunStale(
                  runId, run.processName(), DslRunStatus.STALE,
                  "Run exceeded staleness threshold " + staleThreshold
                          + " without producing a final status",
                  run.startedAt(), finishedAt,
                  finishedAt, run.correlationId()));
          publishStatusChanged(runId, DslRunStatus.STALE.name());
        }
        return null;
      });

      meterRegistry.counter(StarterConstants.SWEEP_STALE_COUNTER,
              StarterConstants.PROCESS_NAME_TAG, safeProcessName(run.processName())).increment();
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
      return StarterConstants.UNKNOWN_PROCESS;
    }
    return GlobalManager.globalManager().processNames().contains(processName)
            ? processName
            : StarterConstants.UNKNOWN_PROCESS;
  }

  private void recordRunComplete(@Nullable String processName, @NonNull String status,
          @NonNull Instant startedAt, @NonNull Instant finishedAt) {
    String safe = safeProcessName(processName);
    Duration duration = Duration.between(startedAt, finishedAt);
    if (duration.isNegative()) {
      duration = Duration.ZERO;
    }
    Timer.builder(StarterConstants.RUN_DURATION_TIMER)
            .description("Duration of a production DSL run")
            .tag(StarterConstants.PROCESS_NAME_TAG, safe)
            .tag(StarterConstants.STATUS_TAG, status)
            .register(meterRegistry)
            .record(duration);
    meterRegistry.counter(StarterConstants.RUN_COUNT_COUNTER,
            StarterConstants.PROCESS_NAME_TAG, safe,
            StarterConstants.STATUS_TAG, status).increment();
  }

  public void recordCancel(@Nullable String processName, @Nullable Instant startedAt,
          @NonNull Outcome outcome, @NonNull Instant finishedAt) {
    String safe = safeProcessName(processName);
    String status = switch (outcome) {
      case CANCELLED -> "cancelled";
      case NOT_FOUND -> "notfound";
      case NOT_CANCELLABLE -> "rejected";
    };
    meterRegistry.counter(StarterConstants.CANCEL_COUNTER,
            StarterConstants.STATUS_TAG, status,
            StarterConstants.PROCESS_NAME_TAG, safe).increment();
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
    return doExecuteAndRecord(processName, body, metadata, runId, startedAt,
            CorrelationId.fromMetadata(metadata.get(StarterConstants.CORRELATION_ID_METADATA_KEY)));
  }

  private @NonNull Result<?> doExecuteAndRecord(
          @NonNull String processName,
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull String runId,
          @NonNull Instant startedAt,
          @Nullable String correlationId) {
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
      String outputJson = result.isSuccess()
              ? serialize(result.value())
              : StarterConstants.EMPTY_OUTPUT_JSON;
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
        publishStatusChanged(runId, status);
        if (DslRunStatus.COMPLETED.name().equals(status)) {
          publishEvent(new DomainEvent.RunCompleted(
                  runId, processName, DslRunStatus.COMPLETED,
                  finalOutputJson, finalError, startedAt, finishedAt,
                  finishedAt, correlationId));
        } else {
          publishEvent(new DomainEvent.RunFailed(
                  runId, processName, DslRunStatus.FAILED,
                  finalError, startedAt, finishedAt,
                  finishedAt, correlationId));
        }
        return null;
      });

      recordRunComplete(processName, status, startedAt, finishedAt);

      webhookDispatcher.ifPresent(
              dispatcher -> dispatcher.onRunComplete(runId, processName, status, startedAt,
                      finishedAt,
                      finalError));

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

  /**
   * Publishes a domain event through the optional {@link DomainEventPublisher} bean.
   *
   * <p>
   * This is intentionally a NO-OP when the publisher is absent (no DataSource / in-memory run
   * repository / tests that disable the event store). When the publisher IS present, the insert
   * runs in the SAME database transaction as the surrounding state-row write when a
   * {@link TransactionTemplate} is also available — see the "T411 same-TX" comments at the call
   * sites in {@code startProcess}, {@code doExecuteAndRecord}, and {@code markStale}. Failure is
   * NOT swallowed: an event-store failure at the run path breaks the surrounding write (per the
   * loop note: event loss defeats the purpose of the store).
   */
  private void publishEvent(@NonNull DomainEvent event) {
    DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      return;
    }
    TransactionTemplate tx = transactionTemplateProvider.getIfAvailable();
    if (tx != null) {
      tx.executeWithoutResult(status -> publisher.publish(event));
    } else {
      publisher.publish(event);
    }
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
