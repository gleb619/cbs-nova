package cbs.nova.starter.service;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.exception.DslException;
import cbs.nova.starter.converter.DslRuntimeMapper;
import cbs.nova.starter.core.pipe.PreviewTimeoutException;
import cbs.nova.starter.logging.LoggingExecutionListener;
import cbs.nova.starter.model.DslRequest;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.RuntimeOutcome;
import cbs.nova.starter.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class DslRuntimeService {

  private final DslRuntime dslRuntime;
  private final ContextFactory contextFactory;
  private final LoggingExecutionListener loggingListener;
  private final DslRuntimeMapper mapper;

  public RuntimeOutcome preview(String name, DslRequest request, @Nullable String requestId) {
    String runId = resolveRunId(requestId);
    Context<?> ctx = toContext(request, ExecutionMode.PREVIEW, runId);
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
    Context<?> ctx = toContext(request, ExecutionMode.RUN, runId, correlationId);
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
    Context<?> ctx = toContext(request, ExecutionMode.EXPLAIN, runId);
    ExplainReport report = executeWithMdc(runId, () -> dslRuntime.explain(name, ctx));
    PreviewErrorDetail timeoutError = report.errors().stream()
            .filter(e -> e.code() == PreviewErrorCode.PREVIEW_TIMEOUT)
            .findFirst()
            .orElse(null);
    if (timeoutError != null) {
      return RuntimeOutcome.error(mapper.toErrorResponse(
              mapper.fromPreviewTimeoutException(name, runId, timeoutError.message())));
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

  private Context<?> toContext(DslRequest request, ExecutionMode mode, String runId) {
    return toContext(request, mode, runId, null);
  }

  private Context<?> toContext(DslRequest request, ExecutionMode mode, String runId,
          @Nullable String correlationId) {
    Map<String, Object> metadata = request.metadata() != null
            ? new java.util.HashMap<>(request.metadata())
            : new java.util.HashMap<>();
    if (correlationId != null && !correlationId.isBlank()) {
      metadata.put(CorrelationId.CORRELATION_ID_METADATA_KEY, correlationId);
    }
    Context<?> ctx = contextFactory.of(request.body(), metadata, mode, runId);
    return ctx.withExecutionListener(loggingListener);
  }

  private <R> R executeWithMdc(String correlationId, Supplier<R> action) {
    boolean put = correlationId != null && !correlationId.isBlank();
    if (put) {
      MDC.put(RequestIdFilter.REQUEST_ID_MDC_KEY, correlationId);
    }
    try {
      return action.get();
    } finally {
      if (put) {
        MDC.remove(RequestIdFilter.REQUEST_ID_MDC_KEY);
      }
    }
  }
}
