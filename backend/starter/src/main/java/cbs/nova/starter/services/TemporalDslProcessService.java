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
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

@AllArgsConstructor
public class TemporalDslProcessService {

  private final ContextFactory contextFactory;
  private final DslRunRepository runRepository;
  private final ObjectMapper objectMapper;
  private final ExecutionTraceCollector traceCollector;

  // TODO: refactor method, make it not blocking, instead if process is running, we need to return
  // an intermidiate Result with correspondent status(pending or something like that)
  public @NonNull Result<?> runProcess(@NonNull String processName, @Nullable Object input) {
    return runProcess(processName, input, Map.of());
  }

  public @NonNull Result<?> runProcess(
          @NonNull String processName,
          @Nullable Object input,
          @NonNull Map<String, Object> metadata) {
    return startProcess(processName, input, metadata).result().join();
  }

  /**
   * Starts a process asynchronously, returning as soon as the {@link DslRun} is recorded as
   * {@code RUNNING} and the workflow has been launched. The returned handle exposes the generated
   * {@code runId} (useful for correlating side effects such as latch files) and a future that
   * completes with the outcome once the workflow finishes and the run is recorded as
   * {@code COMPLETED} or {@code FAILED}.
   *
   * <p>
   * This is the non-blocking counterpart of {@link #runProcess(String, Object)} and is required for
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
      Instant startedAt = Instant.now();

      DslRun running = DslRun.builder()
              .runId(runId)
              .processName(processName)
              // TODO: along with running process, we need to start a separater thread, that will
              // make
              // a 'healthchecks' for running process, to check if it alive, via call of some
              // '@Query'
              // method or something like that. And add a new status for staled processes
              .status(DslRunStatus.RUNNING.name())
              .input(inputJson)
              // TODO: instead null, make it empty object
              .output(null)
              .error(null)
              .startedAt(startedAt)
              // TODO: same here
              .finishedAt(null)
              .executionMode(ExecutionMode.RUN.name())
              .build();

      // TODO: db operation must be async
      runRepository.save(running);

      // TODO: we cant use a commoon pool here, we need our own, with speing support(e.g. for
      // security, logs, telemetry, etc, e.g.). We also need to use a teporal async support
      CompletableFuture<Result<?>> result = CompletableFuture.supplyAsync(
              () -> executeAndRecord(processName, body, metadata, runId, startedAt),
              ForkJoinPool.commonPool());
      return new ProcessRun(runId, result);
    } finally {
      try {
        runIdScope.close();
      } catch (Exception ignored) {
      }
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

  private @NonNull Result<?> executeAndRecord(
          @NonNull String processName,
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull String runId,
          @NonNull Instant startedAt) {
    traceCollector.start(runId);
    Context<?> ctx = contextFactory.of(body, metadata, ExecutionMode.RUN, runId);
    Result<?> result;
    try {
      result = GlobalManager.globalManager().runProcess(processName, ctx);
    } catch (Exception ex) {
      result = Result.failure(ex);
    } finally {
      traceCollector.stop(runId);
    }

    Instant finishedAt = Instant.now();
    String contextJson = serializeTrace(traceCollector.snapshot(runId));
    runRepository.updateFinished(
            runId,
            result.isSuccess() ? DslRunStatus.COMPLETED.name() : DslRunStatus.FAILED.name(),
            result.isSuccess() ? serialize(result.value()) : null,
            result.isSuccess() ? null : messageOf(result.cause()),
            finishedAt,
            contextJson);

    return result;
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
  public record ProcessRun(@NonNull String runId, @NonNull CompletableFuture<Result<?>> result) {
  }
}
