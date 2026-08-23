package cbs.nova.starter.controllers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionListener;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.exception.DslException;
import cbs.nova.starter.logging.LoggingExecutionListener;
import cbs.nova.starter.models.ErrorResponse;
import cbs.nova.starter.web.RequestIdFilter;
import jakarta.servlet.ServletException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class DslRuntimeHandler {

  private final DslRuntime dslRuntime;
  private final ContextFactory contextFactory;
  private final LoggingExecutionListener loggingListener;

  public ServerResponse preview(ServerRequest request) throws ServletException, IOException {
    String name = request.pathVariable("name");
    DslRequest dslRequest = request.body(DslRequest.class);
    String requestId = requestId(request);
    var ctx = toContext(dslRequest, ExecutionMode.PREVIEW, requestId);
    Result<PreviewReport> result = executeWithMdc(requestId, () -> dslRuntime.preview(name, ctx));
    PreviewReport report = result.value();
    boolean success = report != null && report.success();
    if (success) {
      return ServerResponse.ok().body(report);
    }
    PreviewErrorDetail firstError = report != null && !report.errors().isEmpty()
            ? report.errors().get(0)
            : null;
    String code = firstError != null ? firstError.code().name() : "EXECUTION_FAILED";
    String message = firstError != null && firstError.message() != null
            ? firstError.message()
            : "Preview failed";
    String runId = ctx.runId();
    String exceptionId = runId + ":ex:" + UUID.randomUUID();
    return ServerResponse.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse(code, message, name, runId, exceptionId));
  }

  public ServerResponse run(ServerRequest request) throws ServletException, IOException {
    String name = request.pathVariable("name");
    DslRequest dslRequest = request.body(DslRequest.class);
    String requestId = requestId(request);
    var ctx = toContext(dslRequest, ExecutionMode.RUN, requestId);
    Result<?> result = executeWithMdc(requestId, () -> dslRuntime.run(name, ctx));
    return result.isSuccess()
            ? ServerResponse.ok().body(result.value())
            : ServerResponse.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(toErrorResponse(name, ctx, result.cause()));
  }

  public ServerResponse explain(ServerRequest request) throws ServletException, IOException {
    String name = request.pathVariable("name");
    DslRequest dslRequest = request.body(DslRequest.class);
    String requestId = requestId(request);
    var ctx = toContext(dslRequest, ExecutionMode.EXPLAIN, requestId);
    ExplainReport report = executeWithMdc(requestId, () -> dslRuntime.explain(name, ctx));
    return ServerResponse.ok().body(report);
  }

  private String requestId(ServerRequest request) {
    String requestId = request.headers().firstHeader(RequestIdFilter.REQUEST_ID_HEADER);
    return requestId != null && !requestId.isBlank() ? requestId : null;
  }

  private Context<?> toContext(DslRequest request, ExecutionMode mode, @Nullable String requestId) {
    Map<String, Object> metadata = request.metadata() != null ? request.metadata() : Map.of();
    String runId = requestId != null && !requestId.isBlank()
            ? requestId
            : contextFactory.generateRunId();
    var ctx = contextFactory.of(request.body(), metadata, mode, runId);
    return ctx.withExecutionListener(loggingListener);
  }

  private <R> R executeWithMdc(@Nullable String requestId, java.util.function.Supplier<R> action) {
    boolean put = requestId != null && !requestId.isBlank();
    if (put) {
      MDC.put(RequestIdFilter.REQUEST_ID_MDC_KEY, requestId);
    }
    try {
      return action.get();
    } finally {
      if (put) {
        MDC.remove(RequestIdFilter.REQUEST_ID_MDC_KEY);
      }
    }
  }

  private ErrorResponse toErrorResponse(String entityName, Context<?> ctx, Throwable cause) {
    if (cause instanceof DslException d) {
      return new ErrorResponse(d.code().name(), d.getMessage(), entityName, d.runId(),
              d.exceptionId());
    }
    String runId = ctx.runId();
    String exceptionId = runId + ":ex:" + UUID.randomUUID();
    String message = cause.getMessage() != null
            ? cause.getMessage()
            : cause.getClass().getSimpleName();
    return new ErrorResponse("EXECUTION_FAILED", message, entityName, runId, exceptionId);
  }

  public record DslRequest(Object body, Map<String, Object> metadata) {

  }
}
