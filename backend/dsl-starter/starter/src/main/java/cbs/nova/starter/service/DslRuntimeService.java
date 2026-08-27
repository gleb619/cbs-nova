package cbs.nova.starter.service;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.exception.DslException;
import cbs.nova.starter.converter.DslRuntimeMapper;
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
 * Owns the orchestration that previously lived in {@code DslRuntimeHandler}: context
 * construction, MDC propagation, execution invocation, and outcome/error mapping.
 *
 * <p>The service is HTTP-agnostic — it never touches Spring's web types. Handlers stay
 * thin: extract path/body/header, delegate here, translate the {@link RuntimeOutcome}
 * back into a {@code ServerResponse}.
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
    PreviewReport report = result.value();
    boolean success = report != null && report.success();
    if (success) {
      return RuntimeOutcome.ok(report);
    }
    return RuntimeOutcome.error(mapper.toErrorResponse(
            mapper.fromPreviewReport(name, runId, report)));
  }

  public RuntimeOutcome run(String name, DslRequest request, @Nullable String requestId) {
    String runId = resolveRunId(requestId);
    Context<?> ctx = toContext(request, ExecutionMode.RUN, runId);
    Result<?> result = executeWithMdc(runId, () -> dslRuntime.run(name, ctx));
    if (result.isSuccess()) {
      return RuntimeOutcome.ok(result.value());
    }
    return RuntimeOutcome.error(toErrorResponse(name, runId, result.cause()));
  }

  public ExplainReport explain(String name, DslRequest request, @Nullable String requestId) {
    String runId = resolveRunId(requestId);
    Context<?> ctx = toContext(request, ExecutionMode.EXPLAIN, runId);
    return executeWithMdc(runId, () -> dslRuntime.explain(name, ctx));
  }

  private ErrorResponse toErrorResponse(String entityName, String runId, Throwable cause) {
    if (cause instanceof DslException d) {
      return mapper.toErrorResponse(mapper.fromDslException(d, entityName));
    }
    return mapper.toErrorResponse(mapper.fromThrowable(entityName, runId, cause));
  }

  private String resolveRunId(@Nullable String requestId) {
    return requestId != null && !requestId.isBlank() ? requestId : contextFactory.generateRunId();
  }

  private Context<?> toContext(DslRequest request, ExecutionMode mode, String runId) {
    Map<String, Object> metadata = request.metadata() != null ? request.metadata() : Map.of();
    Context<?> ctx = contextFactory.of(request.body(), metadata, mode, runId);
    return ctx.withExecutionListener(loggingListener);
  }

  private <R> R executeWithMdc(String runId, Supplier<R> action) {
    boolean put = runId != null && !runId.isBlank();
    if (put) {
      MDC.put(RequestIdFilter.REQUEST_ID_MDC_KEY, runId);
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