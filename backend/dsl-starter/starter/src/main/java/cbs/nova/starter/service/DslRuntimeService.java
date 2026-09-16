package cbs.nova.starter.service;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.exception.DslException;
import cbs.nova.starter.converter.DslRuntimeMapper;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.pipe.PreviewTimeoutException;
import cbs.nova.starter.logging.LoggingExecutionListener;
import cbs.nova.starter.model.DslRequest;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.model.RuntimeOutcome;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Owns the orchestration that previously lived in {@code DslRuntimeHandler}: context construction,
 * MDC propagation, execution invocation, and outcome/error mapping.
 *
 * <p>
 * The service is HTTP-agnostic — it never touches Spring's web types. Handlers stay thin: extract
 * path/body/header, delegate here, translate the {@link RuntimeOutcome} back into a
 * {@code ServerResponse}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DslRuntimeService {

  private final DslRuntime dslRuntime;
  private final ContextFactory contextFactory;
  private final LoggingExecutionListener loggingListener;
  private final DslRuntimeMapper mapper;

  public RuntimeOutcome preview(String name, DslRequest request, @Nullable String requestId) {
    String runId = resolveRunId(requestId);
    Context<?> ctx = toContext(name, request, ExecutionMode.PREVIEW, runId);
    Result<PreviewReport> result = executeWithMdc(runId, () -> dslRuntime.preview(name, ctx));
    if (!result.isSuccess() && result.cause() instanceof PreviewTimeoutException cause) {
      return RuntimeOutcome.error(mapper.toErrorResponse(
              mapper.fromPreviewTimeoutException(name, runId, cause)));
    }
    PreviewReport report = result.value();
    boolean success = report != null && report.success();
    if (success) {
      return RuntimeOutcome.ok(report);
    }
    return RuntimeOutcome.error(mapper.toErrorResponse(
            mapper.fromPreviewReport(name, runId, report)));
  }

  public RuntimeOutcome run(String name, DslRequest request, @Nullable String requestId) {
    return run(name, request, requestId, null, null);
  }

  public RuntimeOutcome run(String name, DslRequest request, @Nullable String requestId,
          @Nullable String forcedRunId) {
    return run(name, request, requestId, forcedRunId, null);
  }

  public RuntimeOutcome run(String name, DslRequest request, @Nullable String requestId,
          @Nullable String forcedRunId, @Nullable String correlationId) {
    String runId = forcedRunId != null ? forcedRunId : resolveRunId(requestId);
    String mdcRunId = requestId != null && !requestId.isBlank() ? requestId : runId;
    Context<?> ctx = toContext(name, request, ExecutionMode.RUN, runId, correlationId);
    Result<?> result = executeWithMdc(mdcRunId, () -> dslRuntime.run(name, ctx));
    if (result.isSuccess()) {
      return RuntimeOutcome.ok(result.value());
    }
    if (result.cause() instanceof IdempotentReplayException replay) {
      return RuntimeOutcome.okReplayed(Map.of("runId", replay.runId(), "status", "REPLAYED"));
    }
    return RuntimeOutcome.error(toErrorResponse(name, runId, result.cause()));
  }

  public RuntimeOutcome explain(String name, DslRequest request, @Nullable String requestId) {
    String runId = resolveRunId(requestId);
    Context<?> ctx = toContext(name, request, ExecutionMode.EXPLAIN, runId);
    final ExplainReport report;
    try {
      report = executeWithMdc(runId, () -> dslRuntime.explain(name, ctx));
    } catch (RuntimeException ex) {
      return RuntimeOutcome.error(toErrorResponse(name, runId, ex));
    }
    if (report == null) {
      return RuntimeOutcome.error(toErrorResponse(name, runId,
              new IllegalStateException("explain produced no report for " + name)));
    }
    return RuntimeOutcome.ok(report);
  }

  private ErrorResponse toErrorResponse(String entityName, String runId, Throwable cause) {
    if (cause instanceof DslException d) {
      return mapper.toErrorResponse(mapper.fromDslException(d, entityName));
    }
    if (cause instanceof PreviewTimeoutException timeout) {
      return mapper.toErrorResponse(mapper.fromPreviewTimeoutException(entityName, runId, timeout));
    }
    return mapper.toErrorResponse(mapper.fromThrowable(entityName, runId, cause));
  }

  private String resolveRunId(@Nullable String requestId) {
    return requestId != null && !requestId.isBlank() ? requestId : contextFactory.generateRunId();
  }

  private Context<?> toContext(String name, DslRequest request, ExecutionMode mode, String runId) {
    return toContext(name, request, mode, runId, null);
  }

  private Context<?> toContext(String name, DslRequest request, ExecutionMode mode,
          String runId, @Nullable String correlationId) {
    Map<String, Object> metadata = request.metadata() != null
            ? new HashMap<>(request.metadata())
            : new HashMap<>();
    if (correlationId != null && !correlationId.isBlank()) {
      metadata.put(StarterConstants.CORRELATION_ID_METADATA_KEY, correlationId);
    }
    Object body = coerceBody(name, runId, request.body());
    Context<?> ctx = contextFactory.of(body, metadata, mode, runId);
    return ctx.withExecutionListener(loggingListener);
  }

  /**
   * Coerce an inbound {@code Map<String,Object>} body into the typed input record declared on the
   * target construct (process / transaction). HTTP callers send JSON which Spring binds as a
   * {@link java.util.LinkedHashMap}; user DSL code reads {@code ctx.body()} and casts to a typed
   * record (e.g. {@code BatchIn}). Without this coercion, the cast at the user code site throws
   * {@code ClassCastException} and the request fails with HTTP 422.
   *
   * <p>
   * Best-effort: if the construct cannot be resolved, the body is not a map, or conversion throws,
   * the original body is returned untouched and the existing failure mode applies.
   */
  private Object coerceBody(String name, String runId, Object body) {
    if (!(body instanceof Map<?, ?> rawMap)) {
      return body;
    }
    Class<?> inputType = resolveInputType(name, runId);
    if (inputType == null) {
      return body;
    }
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) rawMap;
      return DslConfig.dslConfig().avajeMapConverter().fromMap(map, inputType);
    } catch (RuntimeException ex) {
      log.warn("[runId:{}] failed to coerce map body to {} — passing raw map downstream",
              runId, inputType.getName(), ex);
      return body;
    }
  }

  @Nullable
  private Class<?> resolveInputType(String name, String runId) {
    try {
      GlobalManager gm = GlobalManager.globalManager();
      // The service does not know whether {@code name} refers to a process or transaction.
      // Probe both registries; helper/function constructs are not affected by this path.
      Class<?> fromProcess = gm.findProcess(name).map(p -> p.inputType()).orElse(null);
      if (fromProcess != null) {
        return fromProcess;
      }
      return gm.findTransaction(name).map(t -> t.inputType()).orElse(null);
    } catch (RuntimeException ex) {
      log.warn("[runId:{}] could not resolve input type for {} — skipping coercion",
              runId, name, ex);
      return null;
    }
  }

  private <R> R executeWithMdc(String correlationId, Supplier<R> action) {
    boolean put = correlationId != null && !correlationId.isBlank();
    if (put) {
      MDC.put(StarterConstants.REQUEST_ID_MDC_KEY, correlationId);
    }
    try {
      return action.get();
    } finally {
      if (put) {
        MDC.remove(StarterConstants.REQUEST_ID_MDC_KEY);
      }
    }
  }
}
