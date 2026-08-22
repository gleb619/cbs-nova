package cbs.nova.starter.controllers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.exception.DslException;
import cbs.nova.starter.models.ErrorResponse;
import jakarta.servlet.ServletException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Functional handler for the DSL runtime endpoints. Registered as a {@code RouterFunction} bean by
 * {@link cbs.nova.starter.config.DslRuntimeRouterConfiguration} rather than as a hardcoded
 * {@code @RestController}, following the same pattern as DSL executions and introspection.
 */
@RequiredArgsConstructor
public class DslRuntimeHandler {

  private final DslRuntime dslRuntime;
  private final ContextFactory contextFactory;

  public ServerResponse preview(ServerRequest request) throws ServletException, IOException {
    String name = request.pathVariable("name");
    DslRequest dslRequest = request.body(DslRequest.class);
    var ctx = toContext(dslRequest, ExecutionMode.PREVIEW);
    Result<PreviewReport> result = dslRuntime.preview(name, ctx);
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
    var ctx = toContext(dslRequest, ExecutionMode.RUN);
    Result<?> result = dslRuntime.run(name, ctx);
    return result.isSuccess()
            ? ServerResponse.ok().body(result.value())
            : ServerResponse.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(toErrorResponse(name, ctx, result.cause()));
  }

  public ServerResponse explain(ServerRequest request) throws ServletException, IOException {
    String name = request.pathVariable("name");
    DslRequest dslRequest = request.body(DslRequest.class);
    var ctx = toContext(dslRequest, ExecutionMode.EXPLAIN);
    ExplainReport report = dslRuntime.explain(name, ctx);
    return ServerResponse.ok().body(report);
  }

  private Context<?> toContext(DslRequest request, ExecutionMode mode) {
    Map<String, Object> metadata = request.metadata() != null ? request.metadata() : Map.of();
    return contextFactory.of(request.body(), metadata, mode, contextFactory.generateRunId());
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
