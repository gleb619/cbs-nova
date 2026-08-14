package cbs.nova.starter.controllers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.exception.DslException;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.models.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dsl")
@Tag(name = "DSL Runtime", description = "Execute DSL processes and transactions")
@RequiredArgsConstructor
public class DslRuntimeResource {

  private final DslRuntime dslRuntime;
  private final ContextFactory contextFactory;

  @PostMapping("/preview/{name}")
  @Operation(summary = "Preview a DSL process without side effects")
  @ApiResponse(responseCode = "200", description = "Dry-run preview report including AST, execution trace, and captured external calls", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PreviewReport.class)))
  public ResponseEntity<?> preview(
          @PathVariable String name, @RequestBody DslRequest request) {
    var ctx = toContext(request, ExecutionMode.PREVIEW);
    Result<PreviewReport> result = dslRuntime.preview(name, ctx);
    PreviewReport report = result.value();
    boolean success = report != null && report.success();
    if (success) {
      return ResponseEntity.ok(report);
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
    return ResponseEntity.unprocessableEntity()
            .body(new ErrorResponse(code, message, name, runId, exceptionId));
  }

  @PostMapping("/run/{name}")
  @Operation(summary = "Execute a DSL process with full side effects")
  public ResponseEntity<?> run(
          @PathVariable String name, @RequestBody DslRequest request) {
    var ctx = toContext(request, ExecutionMode.RUN);
    Result<?> result = dslRuntime.run(name, ctx);
    return result.isSuccess()
            ? ResponseEntity.ok(result.value())
            : ResponseEntity.unprocessableEntity()
                    .body(toErrorResponse(name, ctx, result.cause()));
  }

  @PostMapping("/explain/{name}")
  @Operation(summary = "Return a static analysis report of a DSL process")
  @ApiResponse(responseCode = "200", description = "Static analysis report including diagrams, AST, and dry-run logs", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExplainReport.class)))
  public ResponseEntity<ExplainReport> explain(
          @PathVariable String name, @RequestBody DslRequest request) {
    var ctx = toContext(request, ExecutionMode.EXPLAIN);
    ExplainReport report = dslRuntime.explain(name, ctx);
    return ResponseEntity.ok(report);
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
